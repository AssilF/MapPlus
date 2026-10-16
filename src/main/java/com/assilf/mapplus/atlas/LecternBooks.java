package com.assilf.mapplus.atlas;

import com.assilf.mapplus.net.LecternBookPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells players with the mod what the lecterns around them hold, so they see
 * each book lying open on its lectern at its page. Vanilla keeps a lectern's
 * book on the server; players without the mod see the closed book as ever.
 * A book is sent once; after that, only its page as it turns.
 */
public final class LecternBooks {
	/** How often lecterns around players are looked over, in ticks. */
	private static final int EVERY = 10;
	/** How far around a player lecterns are shown, in blocks. */
	private static final int REACH = 24;

	/** What each player has been sent: each lectern's book and page. */
	private record Sent(int book, int page) {
	}

	private static final Map<UUID, Map<BlockPos, Sent>> SENT = new HashMap<>();

	private LecternBooks() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LecternBooks::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUUID()));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, LecternBookPayload.TYPE)) {
				look(player);
			}
		}
	}

	private static void look(ServerPlayer player) {
		ServerLevel level = player.level();
		Map<BlockPos, Sent> before = SENT.getOrDefault(player.getUUID(), Map.of());
		Map<BlockPos, Sent> now = new HashMap<>();
		int chunkX = player.getBlockX() >> 4;
		int chunkZ = player.getBlockZ() >> 4;
		int chunks = (REACH >> 4) + 1;
		for (int cx = chunkX - chunks; cx <= chunkX + chunks; cx++) {
			for (int cz = chunkZ - chunks; cz <= chunkZ + chunks; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity entity : chunk.getBlockEntities().values()) {
					if (!(entity instanceof LecternBlockEntity lectern) || !lectern.hasBook()
							|| entity.getBlockPos().distSqr(player.blockPosition()) > (double) REACH * REACH) {
						continue;
					}
					ItemStack book = lectern.getBook();
					BlockPos pos = entity.getBlockPos().immutable();
					Sent sent = new Sent(ItemStack.hashItemAndComponents(book), lectern.getPage());
					now.put(pos, sent);
					Sent was = before.get(pos);
					if (was == null || was.book() != sent.book()) {
						ServerPlayNetworking.send(player, new LecternBookPayload(pos, sent.page(), false, book.copy()));
					} else if (was.page() != sent.page()) {
						ServerPlayNetworking.send(player, new LecternBookPayload(pos, sent.page(), true, ItemStack.EMPTY));
					}
				}
			}
		}
		for (BlockPos pos : before.keySet()) {
			if (!now.containsKey(pos)) {
				ServerPlayNetworking.send(player, new LecternBookPayload(pos, 0, false, ItemStack.EMPTY));
			}
		}
		if (now.isEmpty()) {
			SENT.remove(player.getUUID());
		} else {
			SENT.put(player.getUUID(), now);
		}
	}
}
