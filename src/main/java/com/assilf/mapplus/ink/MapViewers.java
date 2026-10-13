package com.assilf.mapplus.ink;

import com.assilf.mapplus.mixin.MapItemSavedDataAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Players who look at a map without carrying it: on a drawing desk, or in an
 * atlas on a lectern. They receive the map's updates like players who carry
 * it, and stop as soon as they look away, so maps don't keep hold of them.
 */
public final class MapViewers {
	private static final Map<ServerPlayer, Set<MapId>> VIEWING = new HashMap<>();

	private MapViewers() {
	}

	public static void register() {
		// After the desk and the atlases have registered this tick's viewers.
		ServerTickEvents.END_SERVER_TICK.register(MapViewers::releaseLookedAway);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Set<MapId> maps = VIEWING.remove(handler.player);
			if (maps != null) {
				maps.forEach(mapId -> release(server, handler.player, mapId));
			}
		});
	}

	/** Shows a map to a player who looks at it, sending whatever changed since they last saw it. */
	public static void view(ServerPlayer player, MapId mapId, MapItemSavedData map) {
		map.getHoldingPlayer(player);
		VIEWING.computeIfAbsent(player, p -> new HashSet<>()).add(mapId);
		Packet<?> packet = map.getUpdatePacket(mapId, player);
		if (packet != null) {
			player.connection.send(packet);
		}
	}

	private static void releaseLookedAway(MinecraftServer server) {
		Iterator<Map.Entry<ServerPlayer, Set<MapId>>> players = VIEWING.entrySet().iterator();
		while (players.hasNext()) {
			Map.Entry<ServerPlayer, Set<MapId>> entry = players.next();
			ServerPlayer player = entry.getKey();
			Iterator<MapId> maps = entry.getValue().iterator();
			while (maps.hasNext()) {
				MapId mapId = maps.next();
				if (player.isRemoved() || !DrawingDesk.isViewing(player, mapId)) {
					release(server, player, mapId);
					maps.remove();
				}
			}
			if (entry.getValue().isEmpty()) {
				players.remove();
			}
		}
	}

	/**
	 * Stops sending a map to a player. If they also carry it, vanilla registers
	 * them again on their next tick.
	 */
	private static void release(MinecraftServer server, ServerPlayer player, MapId mapId) {
		MapItemSavedData map = server.overworld().getMapData(mapId);
		if (map == null) {
			return;
		}
		MapItemSavedDataAccessor accessor = (MapItemSavedDataAccessor) map;
		MapItemSavedData.HoldingPlayer holder = accessor.mapplus$carriedByPlayers().remove(player);
		if (holder != null) {
			accessor.mapplus$carriedBy().remove(holder);
		}
	}
}
