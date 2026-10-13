package com.assilf.mapplus.client.desk;

import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.InkMaterial;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.material.MapColor;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Strokes sent to the server but not yet back in the map, drawn over the
 * canvas so drawing feels immediate. Each pixel is cleared once the server
 * acknowledges its stroke, by which time the map itself shows it.
 */
final class InkPreview {
	private static final int GLOW_TINT = 0x66FFFFFF;
	private static final int ERASE_TINT = 0xB0D6BE96;
	private static final int FADE_TINT = 0x60D6BE96;

	private final int[] colors = new int[Ink.PIXELS];
	private final int[] strokes = new int[Ink.PIXELS];
	private int count;

	/** The color a material shows on a pixel before the server confirms it, at a strength. */
	static int previewColor(InkMaterial material, int strength) {
		return switch (material.kind()) {
			case DYE -> Math.round(Ink.opacity(strength) * 255) << 24
					| material.dye().getMapColor().calculateARGBColor(MapColor.Brightness.NORMAL) & 0xFFFFFF;
			case GLOW -> GLOW_TINT;
			// Fading only lightens the ink; erasing shows the map's paper.
			case WATER -> strength == Ink.FULL ? ERASE_TINT : FADE_TINT;
		};
	}

	void add(BitSet pixels, int seq, int color) {
		for (int i = pixels.nextSetBit(0); i >= 0 && i < Ink.PIXELS; i = pixels.nextSetBit(i + 1)) {
			if (colors[i] == 0) {
				count++;
			}
			colors[i] = color;
			strokes[i] = seq;
		}
	}

	/** Drops the pixels of every stroke up to this one. */
	void acknowledge(int ack) {
		if (count == 0) {
			return;
		}
		for (int i = 0; i < Ink.PIXELS; i++) {
			if (colors[i] != 0 && strokes[i] <= ack) {
				colors[i] = 0;
				count--;
			}
		}
	}

	/** The pixels of strokes not back from the server yet. */
	BitSet pending() {
		BitSet pending = new BitSet(Ink.PIXELS);
		if (count == 0) {
			return pending;
		}
		for (int i = 0; i < Ink.PIXELS; i++) {
			if (colors[i] != 0) {
				pending.set(i);
			}
		}
		return pending;
	}

	void clear() {
		Arrays.fill(colors, 0);
		count = 0;
	}

	void extract(GuiGraphicsExtractor graphics, int x, int y) {
		if (count > 0) {
			drawRuns(graphics, x, y, colors);
		}
	}

	/** Draws a pixel layer as one fill per run of same-colored pixels in a row. */
	static void drawRuns(GuiGraphicsExtractor graphics, int x, int y, int[] layer) {
		for (int row = 0; row < Ink.SIZE; row++) {
			int offset = row * Ink.SIZE;
			int column = 0;
			while (column < Ink.SIZE) {
				int color = layer[offset + column];
				if (color == 0) {
					column++;
					continue;
				}
				int end = column + 1;
				while (end < Ink.SIZE && layer[offset + end] == color) {
					end++;
				}
				graphics.fill(x + column, y + row, x + end, y + row + 1, color);
				column = end;
			}
		}
	}

	/** Draws a set of pixels in one color, a run per row. */
	static void drawRuns(GuiGraphicsExtractor graphics, int x, int y, BitSet pixels, int color) {
		int i = pixels.nextSetBit(0);
		while (i >= 0 && i < Ink.PIXELS) {
			int row = i / Ink.SIZE;
			int rowEnd = (row + 1) * Ink.SIZE;
			int end = pixels.nextClearBit(i);
			end = Math.min(end, rowEnd);
			graphics.fill(x + i % Ink.SIZE, y + row, x + i % Ink.SIZE + (end - i), y + row + 1, color);
			i = pixels.nextSetBit(end);
		}
	}
}
