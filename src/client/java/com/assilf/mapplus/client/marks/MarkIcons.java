package com.assilf.mapplus.client.marks;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.ink.Mark;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The pictures of pins and icons, made from the game's own textures, so
 * resource packs change them too: a pin is the front of its banner's flag,
 * with its patterns; an icon is the picture on a pottery sherd's pot side,
 * cut out of the pot so it can be stamped in any ink.
 */
public final class MarkIcons {
	/** The front of a banner's flag, in its 64 by 64 texture. */
	private static final int FLAG_X = 1;
	private static final int FLAG_Y = 1;
	public static final int FLAG_WIDTH = 20;
	public static final int FLAG_HEIGHT = 40;
	/** The picture on a pot side, inside its rim and its shaded edges. */
	private static final int SYMBOL_X = 2;
	private static final int SYMBOL_Y = 3;
	public static final int SYMBOL_WIDTH = 12;
	public static final int SYMBOL_HEIGHT = 9;
	/** A pot side's pixel is part of the picture when it's this much darker than the pot around it. */
	private static final float SYMBOL_DARKER = 0.88F;

	/** A missing picture, so it isn't looked for again every frame. */
	private static final Identifier NONE = MapPlus.id("none");

	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private static int made;

	private MarkIcons() {
	}

	/** The picture of a pin's banner, or null if it can't be made. */
	public static Identifier banner(Mark mark) {
		StringBuilder key = new StringBuilder("banner/").append(mark.color().getId());
		for (Mark.Layer layer : mark.layers()) {
			key.append('/').append(layer.asset()).append('=').append(layer.color().getId());
		}
		return cached(key.toString(), () -> makeBanner(mark));
	}

	/** The picture of an icon, white where it's drawn, or null if it can't be made. */
	public static Identifier symbol(String asset) {
		return cached("symbol/" + asset, () -> makeSymbol(asset));
	}

	private interface Maker {
		NativeImage make() throws IOException;
	}

	private static Identifier cached(String key, Maker maker) {
		Identifier texture = TEXTURES.get(key);
		if (texture == null) {
			texture = NONE;
			try {
				NativeImage image = maker.make();
				if (image != null) {
					texture = MapPlus.id("mark_icon/" + made++);
					DynamicTexture dynamic = new DynamicTexture(() -> "MapPlus " + key, image);
					dynamic.upload();
					Minecraft.getInstance().getTextureManager().register(texture, dynamic);
				}
			} catch (IOException | RuntimeException e) {
				MapPlus.LOGGER.warn("Couldn't make the picture for {}: {}", key, e.toString());
			}
			TEXTURES.put(key, texture);
		}
		return texture == NONE ? null : texture;
	}

	private static NativeImage makeBanner(Mark mark) throws IOException {
		NativeImage flag = new NativeImage(FLAG_WIDTH, FLAG_HEIGHT, false);
		try (NativeImage base = read(texture("minecraft:base", "textures/entity/banner/"))) {
			if (base == null) {
				flag.close();
				return null;
			}
			int tint = mark.color().getTextureDiffuseColor();
			for (int y = 0; y < FLAG_HEIGHT; y++) {
				for (int x = 0; x < FLAG_WIDTH; x++) {
					flag.setPixel(x, y, ARGB.opaque(ARGB.multiply(base.getPixel(FLAG_X + x, FLAG_Y + y), tint)));
				}
			}
		}
		for (Mark.Layer layer : mark.layers()) {
			try (NativeImage pattern = read(texture(layer.asset(), "textures/entity/banner/"))) {
				if (pattern == null) {
					continue;
				}
				int tint = layer.color().getTextureDiffuseColor();
				for (int y = 0; y < FLAG_HEIGHT; y++) {
					for (int x = 0; x < FLAG_WIDTH; x++) {
						int over = ARGB.multiply(pattern.getPixel(FLAG_X + x, FLAG_Y + y), tint | 0xFF000000);
						flag.setPixel(x, y, over(flag.getPixel(x, y), over));
					}
				}
			}
		}
		return flag;
	}

	private static NativeImage makeSymbol(String asset) throws IOException {
		try (NativeImage side = read(texture(asset, "textures/entity/decorated_pot/"))) {
			if (side == null || side.getWidth() < SYMBOL_X + SYMBOL_WIDTH || side.getHeight() < SYMBOL_Y + SYMBOL_HEIGHT) {
				return null;
			}
			// The pot's own color is the most common shade around the picture; the picture is darker.
			float[] light = new float[SYMBOL_WIDTH * SYMBOL_HEIGHT];
			for (int y = 0; y < SYMBOL_HEIGHT; y++) {
				for (int x = 0; x < SYMBOL_WIDTH; x++) {
					int color = side.getPixel(SYMBOL_X + x, SYMBOL_Y + y);
					light[x + y * SYMBOL_WIDTH] = ARGB.red(color) * 0.299F + ARGB.green(color) * 0.587F + ARGB.blue(color) * 0.114F;
				}
			}
			float[] sorted = light.clone();
			Arrays.sort(sorted);
			float pot = sorted[sorted.length / 2];
			NativeImage symbol = new NativeImage(SYMBOL_WIDTH, SYMBOL_HEIGHT, true);
			for (int y = 0; y < SYMBOL_HEIGHT; y++) {
				for (int x = 0; x < SYMBOL_WIDTH; x++) {
					symbol.setPixel(x, y, light[x + y * SYMBOL_WIDTH] < pot * SYMBOL_DARKER ? 0xFFFFFFFF : 0);
				}
			}
			return symbol;
		}
	}

	/** A texture of the game named by an asset id, as in {@code minecraft:creeper}. */
	private static Identifier texture(String asset, String folder) {
		Identifier id = Identifier.tryParse(asset);
		return id == null ? null : id.withPath(folder + id.getPath() + ".png");
	}

	private static NativeImage read(Identifier texture) throws IOException {
		if (texture == null) {
			return null;
		}
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream in = resource.get().open()) {
			return NativeImage.read(in);
		}
	}

	/** One color laid over another, by the top one's opacity. */
	private static int over(int under, int top) {
		float alpha = ARGB.alpha(top) / 255.0F;
		return ARGB.color(255,
				Math.round(ARGB.red(under) + (ARGB.red(top) - ARGB.red(under)) * alpha),
				Math.round(ARGB.green(under) + (ARGB.green(top) - ARGB.green(under)) * alpha),
				Math.round(ARGB.blue(under) + (ARGB.blue(top) - ARGB.blue(under)) * alpha));
	}

	/** Forgets the pictures, so they're made again from the textures in use. */
	public static void clear() {
		for (Identifier texture : TEXTURES.values()) {
			if (texture != NONE) {
				Minecraft.getInstance().getTextureManager().release(texture);
			}
		}
		TEXTURES.clear();
	}
}
