package com.assilf.mapplus.client.atlas;

import com.assilf.mapplus.atlas.Sketch;
import com.assilf.mapplus.ink.Ink;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.material.MapColor;

/** Draws sketches on book pages, a pixel to a point of the page, as text would sit. */
public final class SketchView {
	private SketchView() {
	}

	/** The color a pixel of ink shows on paper: the dye's map color, lighter when glowing, as strong as the ink. */
	public static int color(byte ink) {
		if (!Ink.isPresent(ink)) {
			return 0;
		}
		MapColor.Brightness brightness = Ink.isGlow(ink) ? MapColor.Brightness.HIGH : MapColor.Brightness.NORMAL;
		int rgb = Ink.dye(ink).getMapColor().calculateARGBColor(brightness) & 0xFFFFFF;
		return Math.round(Ink.opacity(Ink.strength(ink)) * 255) << 24 | rgb;
	}

	/** Draws a sketch's ink with its top left at a point, one fill per run of a color in a row. */
	public static void draw(GuiGraphicsExtractor graphics, byte[] ink, int x, int y) {
		for (int row = 0; row < Sketch.HEIGHT; row++) {
			int offset = row * Sketch.WIDTH;
			int column = 0;
			while (column < Sketch.WIDTH) {
				byte pixel = ink[offset + column];
				if (!Ink.isPresent(pixel)) {
					column++;
					continue;
				}
				int end = column + 1;
				while (end < Sketch.WIDTH && ink[offset + end] == pixel) {
					end++;
				}
				graphics.fill(x + column, y + row, x + end, y + row + 1, color(pixel));
				column = end;
			}
		}
	}
}
