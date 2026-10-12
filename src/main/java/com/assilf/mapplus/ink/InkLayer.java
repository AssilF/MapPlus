package com.assilf.mapplus.ink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * The ink drawn over one map. It never touches the map's own colors.
 */
public final class InkLayer {
	private static final Codec<byte[]> PIXELS_CODEC = Codec.BYTE_BUFFER.xmap(
			buffer -> {
				byte[] pixels = new byte[Ink.PIXELS];
				ByteBuffer copy = buffer.duplicate();
				copy.get(pixels, 0, Math.min(copy.remaining(), Ink.PIXELS));
				return pixels;
			},
			ByteBuffer::wrap
	);

	public static final Codec<InkLayer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			PIXELS_CODEC.fieldOf("pixels").forGetter(layer -> layer.pixels),
			Codec.BOOL.optionalFieldOf("sealed", false).forGetter(layer -> layer.sealed)
	).apply(instance, InkLayer::new));

	private final byte[] pixels;
	private boolean sealed;

	public InkLayer() {
		this(new byte[Ink.PIXELS], false);
	}

	private InkLayer(byte[] pixels, boolean sealed) {
		this.pixels = pixels;
		this.sealed = sealed;
	}

	public byte get(int index) {
		return pixels[index];
	}

	public void set(int index, byte ink) {
		pixels[index] = ink;
	}

	public byte[] pixels() {
		return pixels;
	}

	public boolean isSealed() {
		return sealed;
	}

	public void setSealed(boolean sealed) {
		this.sealed = sealed;
	}

	public boolean isEmpty() {
		for (byte pixel : pixels) {
			if (pixel != Ink.NONE) {
				return false;
			}
		}
		return true;
	}

	public void clear() {
		Arrays.fill(pixels, Ink.NONE);
	}

	/**
	 * Draws this layer over a rectangle of map colors, in place.
	 */
	public void overlay(int startX, int startY, int width, int height, byte[] colors) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				byte ink = pixels[(startX + x) + (startY + y) * Ink.SIZE];
				if (Ink.isPresent(ink)) {
					colors[x + y * width] = Ink.toMapColor(ink);
				}
			}
		}
	}
}
