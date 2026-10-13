package com.assilf.mapplus.client.ink;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.net.MapInkPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.material.MapColor;
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
	/** For each map with glow ink, a picture of only that ink, drawn at full brightness over the map in the world. */
	private static final Map<Integer, Identifier> GLOW = new HashMap<>();

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
		updateGlow(payload.mapId(), any ? payload.ink() : null);
		// The map's picture is made again with the new ink.
		Minecraft minecraft = Minecraft.getInstance();
		MapId mapId = new MapId(payload.mapId());
		MapItemSavedData data = minecraft.level == null ? null : minecraft.level.getMapData(mapId);
		if (data != null) {
			minecraft.getMapTextureManager().update(mapId, data);
		}
	}

	private static void updateGlow(int mapId, byte[] ink) {
		Minecraft minecraft = Minecraft.getInstance();
		NativeImage image = null;
		if (ink != null) {
			for (int i = 0; i < Ink.PIXELS; i++) {
				if (Ink.isPresent(ink[i]) && Ink.isGlow(ink[i])) {
					if (image == null) {
						image = new NativeImage(Ink.SIZE, Ink.SIZE, true);
					}
					int color = Ink.dye(ink[i]).getMapColor().calculateARGBColor(MapColor.Brightness.HIGH);
					int alpha = Math.round(Ink.opacity(Ink.strength(ink[i])) * 255);
					image.setPixel(i % Ink.SIZE, i / Ink.SIZE, alpha << 24 | color & 0xFFFFFF);
				}
			}
		}
		Identifier old = GLOW.remove(mapId);
		if (old != null) {
			minecraft.getTextureManager().release(old);
		}
		if (image != null) {
			Identifier texture = MapPlus.id("glow/" + mapId);
			DynamicTexture dynamic = new DynamicTexture(() -> "MapPlus glow ink " + mapId, image);
			dynamic.upload();
			minecraft.getTextureManager().register(texture, dynamic);
			GLOW.put(mapId, texture);
		}
	}

	/** The picture of a map's glow ink, or null if it has none. */
	public static Identifier glowTexture(int mapId) {
		return GLOW.get(mapId);
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
		for (Identifier texture : GLOW.values()) {
			Minecraft.getInstance().getTextureManager().release(texture);
		}
		GLOW.clear();
	}
}
