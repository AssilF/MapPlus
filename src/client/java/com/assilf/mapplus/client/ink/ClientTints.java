package com.assilf.mapplus.client.ink;

import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.net.MapTintsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * The watered down ink on each map the player has seen, as the server last
 * sent it. It's blended over the map's own colors when the map's picture is
 * made, so the map shows through it.
 */
public final class ClientTints {
	private static final Map<Integer, byte[]> TINTS = new HashMap<>();

	private ClientTints() {
	}

	public static void accept(MapTintsPayload payload) {
		boolean any = false;
		for (byte pixel : payload.tints()) {
			if (pixel != Ink.NONE) {
				any = true;
				break;
			}
		}
		if (any) {
			TINTS.put(payload.mapId(), payload.tints());
		} else if (TINTS.remove(payload.mapId()) == null) {
			return;
		}
		// The map's picture is made again with the new tints.
		Minecraft minecraft = Minecraft.getInstance();
		MapId mapId = new MapId(payload.mapId());
		MapItemSavedData data = minecraft.level == null ? null : minecraft.level.getMapData(mapId);
		if (data != null) {
			minecraft.getMapTextureManager().update(mapId, data);
		}
	}

	public static byte[] get(int mapId) {
		return TINTS.get(mapId);
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
		TINTS.clear();
	}
}
