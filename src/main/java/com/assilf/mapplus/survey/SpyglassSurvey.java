package com.assilf.mapplus.survey;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.MapPlusConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Looking through a spyglass with a map in the other hand fills the map in
 * with what you see. Runs on the server, so it works for vanilla clients too.
 */
public final class SpyglassSurvey {
	/** Half-angle of the surveyed cone, close to the spyglass's field of view. */
	private static final double CONE_HALF_ANGLE = Math.toRadians(4.0);
	private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
	private static final int SPIRAL_POINTS = 512;

	private static final Map<UUID, Integer> SPIRAL = new HashMap<>();
	private static final Set<PendingChunk> PENDING = new HashSet<>();

	private record PendingChunk(ResourceKey<Level> dimension, long pos) {
	}

	private SpyglassSurvey() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SpyglassSurvey::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SPIRAL.remove(handler.player.getUUID()));
	}

	private static void tick(MinecraftServer server) {
		MapPlusConfig config = MapPlusConfig.get();
		if (!config.spyglassSurvey || config.surveyPixelsPerTick <= 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isScoping()) {
				survey(server, player, config);
			}
		}
	}

	private static void survey(MinecraftServer server, ServerPlayer player, MapPlusConfig config) {
		InteractionHand scopeHand = player.getUsedItemHand();
		ItemStack stack = player.getItemInHand(scopeHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		if (!stack.is(Items.FILLED_MAP)) {
			return;
		}
		MapId mapId = stack.get(DataComponents.MAP_ID);
		if (mapId == null) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		MapItemSavedData map = level.getMapData(mapId);
		if (map == null || map.locked || !map.dimension.equals(level.dimension()) || level.dimensionType().hasCeiling()) {
			return;
		}

		double range = server.getPlayerList().getViewDistance() * 16.0 * config.surveyRangeMultiplier;
		int scale = MapPixels.scaleBlocks(map);
		double step = Math.max(1.0, scale / 2.0);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0E-6) {
			right = new Vec3(1, 0, 0);
		}
		right = right.normalize();
		Vec3 up = right.cross(look).normalize();

		MapPixels.ChunkLookup loaded = MapPixels.ChunkLookup.loaded(level);
		int spiral = SPIRAL.getOrDefault(player.getUUID(), 0);
		int chunkBudget = config.surveyChunksPerTick;

		for (int ray = 0; ray < config.surveyPixelsPerTick; ray++) {
			int n = spiral++ % SPIRAL_POINTS;
			double radius = Math.tan(CONE_HALF_ANGLE * Math.sqrt((n + 0.5) / SPIRAL_POINTS));
			double angle = n * GOLDEN_ANGLE;
			Vec3 dir = look
					.add(right.scale(radius * Math.cos(angle)))
					.add(up.scale(radius * Math.sin(angle)))
					.normalize();

			for (double t = 2.0; t < range; t += step) {
				Vec3 p = eye.add(dir.scale(t));
				if (p.y < level.getMinY()) {
					break;
				}
				int x = (int) Math.floor(p.x);
				int z = (int) Math.floor(p.z);
				ChunkAccess chunk = loaded.get(x >> 4, z >> 4);
				if (chunk == null) {
					if (chunkBudget > 0 && requestChunk(level, map, mapId, new ChunkPos(x >> 4, z >> 4), config)) {
						chunkBudget--;
					}
					break;
				}
				if (p.y <= chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1) {
					fillPixel(level, loaded, map, MapPixels.pixelX(map, x), MapPixels.pixelZ(map, z));
					break;
				}
			}
		}
		SPIRAL.put(player.getUUID(), spiral % SPIRAL_POINTS);
	}

	private static void fillPixel(ServerLevel level, MapPixels.ChunkLookup chunks, MapItemSavedData map, int px, int pz) {
		if (px < 0 || pz < 0 || px >= 128 || pz >= 128) {
			return;
		}
		double north = pz > 0 ? MapPixels.height(level, chunks, map, px, pz - 1) : Double.NaN;
		int color = MapPixels.color(level, chunks, map, px, pz, north);
		if (color >= 0) {
			map.setColor(px, pz, (byte) color);
		}
	}

	/**
	 * Loads (and, if allowed, generates) a chunk in the background, then fills
	 * the map pixels it covers.
	 *
	 * @return whether a request was started
	 */
	private static boolean requestChunk(ServerLevel level, MapItemSavedData map, MapId mapId, ChunkPos pos, MapPlusConfig config) {
		PendingChunk pending = new PendingChunk(level.dimension(), pos.pack());
		if (PENDING.contains(pending)) {
			return false;
		}
		PENDING.add(pending);

		if (config.spyglassGeneratesChunks) {
			load(level, mapId, pos, pending);
		} else {
			level.getChunkSource().chunkMap.read(pos).whenCompleteAsync((tag, error) -> {
				if (error == null && tag.isPresent()) {
					load(level, mapId, pos, pending);
				} else {
					PENDING.remove(pending);
				}
			}, level.getServer());
		}
		return true;
	}

	private static void load(ServerLevel level, MapId mapId, ChunkPos pos, PendingChunk pending) {
		level.getChunkSource().getChunkFuture(pos.x(), pos.z(), ChunkStatus.FULL, true).whenCompleteAsync((result, error) -> {
			PENDING.remove(pending);
			if (error != null) {
				MapPlus.LOGGER.debug("Spyglass could not load chunk {}", pos, error);
				return;
			}
			ChunkAccess chunk = result.orElse(null);
			MapItemSavedData map = level.getMapData(mapId);
			if (chunk == null || map == null || map.locked) {
				return;
			}
			MapPixels.ChunkLookup loaded = MapPixels.ChunkLookup.loaded(level);
			MapPixels.ChunkLookup lookup = (chunkX, chunkZ) ->
					chunkX == pos.x() && chunkZ == pos.z() ? chunk : loaded.get(chunkX, chunkZ);

			int minPx = Math.max(0, MapPixels.pixelX(map, pos.getMinBlockX()));
			int maxPx = Math.min(127, MapPixels.pixelX(map, pos.getMaxBlockX()));
			int minPz = Math.max(0, MapPixels.pixelZ(map, pos.getMinBlockZ()));
			int maxPz = Math.min(127, MapPixels.pixelZ(map, pos.getMaxBlockZ()));
			for (int px = minPx; px <= maxPx; px++) {
				for (int pz = minPz; pz <= maxPz; pz++) {
					fillPixel(level, lookup, map, px, pz);
				}
			}
		}, level.getServer());
	}
}
