package com.assilf.mapplus.client.foresight;

import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.net.MapPlacePayload;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Where maps lie in the world, as the server said. The game's own copy of a
 * map doesn't know: vanilla only sends what to draw.
 */
public final class MapPlaces {
	private static final Map<Integer, MapPlacePayload> PLACES = new HashMap<>();

	private MapPlaces() {
	}

	public static void accept(MapPlacePayload payload) {
		PLACES.put(payload.mapId(), payload);
	}

	public static void clear() {
		PLACES.clear();
	}

	/** Where a map lies, or null if the server hasn't said yet. */
	public static MapPlacePayload get(int mapId) {
		return PLACES.get(mapId);
	}

	/** The map pixel over a block column, which may be off the map. */
	public static int pixelX(MapPlacePayload place, MapItemSavedData map, int x) {
		int s = 1 << map.scale;
		return Math.floorDiv(x, s) - Math.floorDiv(place.centerX(), s) + Ink.SIZE / 2;
	}

	public static int pixelZ(MapPlacePayload place, MapItemSavedData map, int z) {
		int s = 1 << map.scale;
		return Math.floorDiv(z, s) - Math.floorDiv(place.centerZ(), s) + Ink.SIZE / 2;
	}

	/** The first block column a map pixel covers. */
	public static int worldX(MapPlacePayload place, MapItemSavedData map, int px) {
		int s = 1 << map.scale;
		return (Math.floorDiv(place.centerX(), s) + px - Ink.SIZE / 2) * s;
	}

	public static int worldZ(MapPlacePayload place, MapItemSavedData map, int pz) {
		int s = 1 << map.scale;
		return (Math.floorDiv(place.centerZ(), s) + pz - Ink.SIZE / 2) * s;
	}
}
