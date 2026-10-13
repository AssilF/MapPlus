package com.assilf.mapplus.client.ink;

import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.net.MapInkPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything drawn over each map the player has seen, as the server last sent
 * it: ink and nations' land. Watered down ink is blended over the map's own
 * colors when the map's picture is made, so the map shows through it; the rest
 * is in the map's colors already. Foresight projects all of it.
 */
public final class ClientInk {
	private static final Map<Integer, byte[]> INK = new HashMap<>();

	private ClientInk() {
	}

	public static void accept(MapInkPayload payload) {
		boolean any = false;
		for (byte pixel : payload.ink()) {
			if (pixel != Ink.NONE) {
				any = true;
				break;
			}
		}
		if (any) {
			INK.put(payload.mapId(), payload.ink());
		} else if (INK.remove(payload.mapId()) == null) {
			return;
		}
		// The map's picture is made again with the new ink.
		Minecraft minecraft = Minecraft.getInstance();
		MapId mapId = new MapId(payload.mapId());
		MapItemSavedData data = minecraft.level == null ? null : minecraft.level.getMapData(mapId);
		if (data != null) {
			minecraft.getMapTextureManager().update(mapId, data);
		}
	}

	/** The ink over a map, or null if it has none. */
	public static byte[] get(int mapId) {
		return INK.get(mapId);
	}

	/**
	 * A map pixel's color with tinted ink over it: blended by how watered down
	 * the ink is, or the ink alone, see-through, where the map has no color.
	 */
	public static int blend(int under, byte ink) {
		int color = Ink.dye(ink).getMapColor().calculateARGBColor(Ink.brightness(ink));
		float opacity = Ink.opacity(Ink.strength(ink));
		if (ARGB.alpha(under) == 0) {
			return ARGB.color(Math.round(opacity * 255), ARGB.red(color), ARGB.green(color), ARGB.blue(color));
		}
		return ARGB.color(ARGB.alpha(under),
				Math.round(ARGB.red(under) + (ARGB.red(color) - ARGB.red(under)) * opacity),
				Math.round(ARGB.green(under) + (ARGB.green(color) - ARGB.green(under)) * opacity),
				Math.round(ARGB.blue(under) + (ARGB.blue(color) - ARGB.blue(under)) * opacity));
	}

	public static void clear() {
		INK.clear();
	}
}
