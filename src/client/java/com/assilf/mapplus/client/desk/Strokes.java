package com.assilf.mapplus.client.desk;

import com.assilf.mapplus.ink.Ink;

import java.util.BitSet;

/** The pixels each drawing tool covers on a 128 by 128 map. */
public final class Strokes {
	private Strokes() {
	}

	public static boolean inside(int x, int y) {
		return x >= 0 && y >= 0 && x < Ink.SIZE && y < Ink.SIZE;
	}

	/** A square brush: one pixel, two by two, or three by three around the point. */
	public static void dab(BitSet out, int x, int y, int size) {
		int from = -(size - 1) / 2;
		for (int dy = from; dy < from + size; dy++) {
			for (int dx = from; dx < from + size; dx++) {
				if (inside(x + dx, y + dy)) {
					out.set(x + dx + (y + dy) * Ink.SIZE);
				}
			}
		}
	}

	public static void line(BitSet out, int x0, int y0, int x1, int y1, int size) {
		int dx = Math.abs(x1 - x0);
		int dy = -Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int error = dx + dy;
		while (true) {
			dab(out, x0, y0, size);
			if (x0 == x1 && y0 == y1) {
				return;
			}
			int doubled = 2 * error;
			if (doubled >= dy) {
				error += dy;
				x0 += sx;
			}
			if (doubled <= dx) {
				error += dx;
				y0 += sy;
			}
		}
	}

	public static void box(BitSet out, int x0, int y0, int x1, int y1, int size) {
		line(out, x0, y0, x1, y0, size);
		line(out, x1, y0, x1, y1, size);
		line(out, x1, y1, x0, y1, size);
		line(out, x0, y1, x0, y0, size);
	}

	/** The area of one color around a pixel, as the map shows it. */
	public static void fill(BitSet out, byte[] colors, int x, int y) {
		if (!inside(x, y)) {
			return;
		}
		int start = x + y * Ink.SIZE;
		byte color = colors[start];
		int[] stack = new int[Ink.PIXELS];
		int size = 0;
		stack[size++] = start;
		out.set(start);
		while (size > 0) {
			int pixel = stack[--size];
			int px = pixel % Ink.SIZE;
			int py = pixel / Ink.SIZE;
			if (px > 0) {
				size = visit(out, colors, color, pixel - 1, stack, size);
			}
			if (px < Ink.SIZE - 1) {
				size = visit(out, colors, color, pixel + 1, stack, size);
			}
			if (py > 0) {
				size = visit(out, colors, color, pixel - Ink.SIZE, stack, size);
			}
			if (py < Ink.SIZE - 1) {
				size = visit(out, colors, color, pixel + Ink.SIZE, stack, size);
			}
		}
	}

	private static int visit(BitSet out, byte[] colors, byte color, int pixel, int[] stack, int size) {
		if (!out.get(pixel) && colors[pixel] == color) {
			out.set(pixel);
			stack[size++] = pixel;
		}
		return size;
	}
}
