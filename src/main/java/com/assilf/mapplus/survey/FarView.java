package com.assilf.mapplus.survey;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.net.FarViewPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Far view: looking through a spyglass, players with the mod see the land past
 * their render distance, as a map would show it, the height and map color of
 * every 2×2 blocks. The server sends it chunk by chunk along the line of
 * sight, nearest first, loading (and, if allowed, generating) the chunks it
 * needs in the background, a few a tick. A spyglass with Far Sight sees twice
 * as far, and surveys twice as far too.
 */
public final class FarView {
	public static final ResourceKey<Enchantment> FAR_SIGHT = ResourceKey.create(Registries.ENCHANTMENT, MapPlus.id("far_sight"));

	/** How wide around the line of sight the land is sent, either way. */
	private static final double HALF_ANGLE = Math.toRadians(16.0);
	/** Chunks sent per tick, per player. */
	private static final int CHUNKS_PER_TICK = 8;
	/** The most chunks remembered as sent to a player; past that they're forgotten and sent again. */
	private static final int MOST_REMEMBERED = 16384;

	private static final Map<UUID, Viewer> VIEWERS = new HashMap<>();
	private static final Set<Long> PENDING = new HashSet<>();

	/** What a player looking far has been sent. */
	private static final class Viewer {
		ResourceKey<Level> dimension;
		final Set<Long> sent = new HashSet<>();
		/** Chunks loaded in the background for the player, waiting to be sent. */
		final List<FarViewPayload.Patch> ready = new ArrayList<>();
	}

	private FarView() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FarView::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VIEWERS.remove(handler.player.getUUID()));
	}

	/** Whether a stack has an enchantment. */
	public static boolean has(ItemStack stack, ResourceKey<Enchantment> key) {
		for (Holder<Enchantment> enchantment : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).keySet()) {
			if (enchantment.is(key)) {
				return true;
			}
		}
		return false;
	}

	/** How much farther than usual a player's spyglass sees: twice with Far Sight. */
	public static double reach(ServerPlayer player) {
		return player.isScoping() && has(player.getUseItem(), FAR_SIGHT) ? 2.0 : 1.0;
	}

	private static long key(int chunkX, int chunkZ) {
		return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
	}

	private static void tick(MinecraftServer server) {
		MapPlusConfig config = MapPlusConfig.get();
		if (!config.spyglassFarView) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isScoping() && ServerPlayNetworking.canSend(player, FarViewPayload.TYPE)) {
				look(server, player, config);
			}
		}
	}

	private static void look(MinecraftServer server, ServerPlayer player, MapPlusConfig config) {
		ServerLevel level = (ServerLevel) player.level();
		Viewer viewer = VIEWERS.computeIfAbsent(player.getUUID(), uuid -> new Viewer());
		if (!level.dimension().equals(viewer.dimension) || viewer.sent.size() > MOST_REMEMBERED) {
			viewer.dimension = level.dimension();
			viewer.sent.clear();
			viewer.ready.clear();
		}
		int view = Math.min(player.requestedViewDistance(), server.getPlayerList().getViewDistance());
		double near = Math.max(2, view - 1) * 16.0;
		double far = server.getPlayerList().getViewDistance() * 16.0 * config.farViewMultiplier * reach(player);
		List<FarViewPayload.Patch> batch = new ArrayList<>();
		while (!viewer.ready.isEmpty() && batch.size() < CHUNKS_PER_TICK) {
			batch.add(viewer.ready.removeFirst());
		}
		int loads = config.surveyChunksPerTick;
		double yaw = Math.toRadians(player.getYRot());
		// Rings of chunks across the line of sight, nearest first, so the land fills in from the near edge out.
		for (double distance = near; distance <= far && batch.size() < CHUNKS_PER_TICK; distance += 16.0) {
			int across = Math.max(1, (int) Math.ceil(distance * Math.tan(HALF_ANGLE) / 16.0));
			for (int i = -across; i <= across && batch.size() < CHUNKS_PER_TICK; i++) {
				double angle = yaw + HALF_ANGLE * i / across;
				int chunkX = (int) Math.floor(player.getX() - Math.sin(angle) * distance) >> 4;
				int chunkZ = (int) Math.floor(player.getZ() + Math.cos(angle) * distance) >> 4;
				long key = key(chunkX, chunkZ);
				if (viewer.sent.contains(key)) {
					continue;
				}
				ChunkAccess chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
				if (chunk != null) {
					viewer.sent.add(key);
					batch.add(patch(level, chunk, chunkX, chunkZ));
				} else if (loads > 0 && !PENDING.contains(key)) {
					loads--;
					viewer.sent.add(key);
					load(level, player.getUUID(), chunkX, chunkZ, config);
				}
			}
		}
		if (!batch.isEmpty()) {
			ServerPlayNetworking.send(player, new FarViewPayload(batch));
		}
	}

	/** Loads a chunk in the background, generating it if allowed, and readies it for the player. */
	private static void load(ServerLevel level, UUID player, int chunkX, int chunkZ, MapPlusConfig config) {
		long key = key(chunkX, chunkZ);
		PENDING.add(key);
		Runnable fetch = () -> level.getChunkSource().getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, true).whenCompleteAsync((result, error) -> {
			PENDING.remove(key);
			ChunkAccess chunk = error == null ? result.orElse(null) : null;
			Viewer viewer = VIEWERS.get(player);
			if (chunk != null && viewer != null && level.dimension().equals(viewer.dimension) && viewer.ready.size() < FarViewPayload.MOST) {
				viewer.ready.add(patch(level, chunk, chunkX, chunkZ));
			}
		}, level.getServer());
		if (config.spyglassGeneratesChunks) {
			fetch.run();
		} else {
			level.getChunkSource().chunkMap.read(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ)).whenCompleteAsync((tag, error) -> {
				if (error == null && tag.isPresent()) {
					fetch.run();
				} else {
					PENDING.remove(key);
				}
			}, level.getServer());
		}
	}

	/** A chunk's land as a map shows it: the top of each 2×2 blocks, and its map color. */
	public static FarViewPayload.Patch patch(ServerLevel level, ChunkAccess chunk, int chunkX, int chunkZ) {
		short[] heights = new short[FarViewPayload.CELLS];
		byte[] colors = new byte[FarViewPayload.CELLS];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < FarViewPayload.CELLS; i++) {
			int x = (chunkX << 4) + i % FarViewPayload.SIDE * FarViewPayload.STEP;
			int z = (chunkZ << 4) + i / FarViewPayload.SIDE * FarViewPayload.STEP;
			int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			MapColor color = MapColor.NONE;
			while (y > level.getMinY()) {
				pos.set(x, y, z);
				color = chunk.getBlockState(pos).getMapColor(level, pos);
				if (color != MapColor.NONE) {
					break;
				}
				y--;
			}
			heights[i] = (short) y;
			colors[i] = (byte) color.id;
		}
		return new FarViewPayload.Patch(chunkX, chunkZ, heights, colors);
	}
}
