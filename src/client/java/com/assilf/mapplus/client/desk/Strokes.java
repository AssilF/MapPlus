package com.assilf.mapplus.client.desk;

import com.assilf.mapplus.ink.Ink;

import java.util.BitSet;
import java.util.function.IntPredicate;

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

	/**
	 * A dashed line, for borders: dashes of {@code on} pixels with gaps of
	 * {@code off}, counted along the line.
	 */
	public static void dashed(BitSet out, int x0, int y0, int x1, int y1, int on, int off) {
		int dx = Math.abs(x1 - x0);
		int dy = -Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int error = dx + dy;
		int step = 0;
		while (true) {
			if (step % (on + off) < on && inside(x0, y0)) {
				out.set(x0 + y0 * Ink.SIZE);
			}
			step++;
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

	/**
	 * The area a fill covers: on ink, the touching ink of the same color; on the
	 * bare map, everything enclosed by the ink lines around it, whatever the
	 * terrain. Gaps of up to two pixels in a line hold the fill too, so a dashed
	 * border can be filled. Pixels of {@code pending} count as ink of their own.
	 */
	public static void fill(BitSet out, byte[] ink, BitSet pending, int x, int y) {
		if (!inside(x, y)) {
			return;
		}
		int start = x + y * Ink.SIZE;
		BitSet wall = new BitSet(Ink.PIXELS);
		for (int i = 0; i < Ink.PIXELS; i++) {
			if (pending.get(i) || Ink.isPresent(ink[i])) {
				wall.set(i);
			}
		}
		if (wall.get(start)) {
			int key = key(ink, pending, start);
			flood(out, start, pixel -> key(ink, pending, pixel) == key);
			return;
		}
		// Lines grown by a pixel all round close the gaps between dashes.
		BitSet grown = grow(wall);
		BitSet inside = new BitSet(Ink.PIXELS);
		flood(inside, start, pixel -> !grown.get(pixel));
		// Then the fill reaches back up to the lines it stopped short of, but not through their gaps.
		out.or(grow(inside));
		out.andNot(wall);
	}

	/** A set of pixels with every pixel around them added, diagonals included. */
	private static BitSet grow(BitSet pixels) {
		BitSet grown = new BitSet(Ink.PIXELS);
		for (int i = pixels.nextSetBit(0); i >= 0 && i < Ink.PIXELS; i = pixels.nextSetBit(i + 1)) {
			int px = i % Ink.SIZE;
			int py = i / Ink.SIZE;
			for (int dy = -1; dy <= 1; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					if (inside(px + dx, py + dy)) {
						grown.set(px + dx + (py + dy) * Ink.SIZE);
					}
				}
			}
		}
		return grown;
	}

	/** The pixels reachable from a start pixel through pixels that pass, side by side. */
	private static void flood(BitSet out, int start, IntPredicate passes) {
		int[] stack = new int[Ink.PIXELS];
		int size = 0;
		stack[size++] = start;
		out.set(start);
		while (size > 0) {
			int pixel = stack[--size];
			int px = pixel % Ink.SIZE;
			int py = pixel / Ink.SIZE;
			if (px > 0) {
				size = visit(out, passes, pixel - 1, stack, size);
			}
			if (px < Ink.SIZE - 1) {
				size = visit(out, passes, pixel + 1, stack, size);
			}
			if (py > 0) {
				size = visit(out, passes, pixel - Ink.SIZE, stack, size);
			}
			if (py < Ink.SIZE - 1) {
				size = visit(out, passes, pixel + Ink.SIZE, stack, size);
			}
		}
	}

	/** What a pixel counts as for a fill: its ink's color, no ink, or a stroke still on its way. */
	private static int key(byte[] ink, BitSet pending, int pixel) {
		if (pending.get(pixel)) {
			return -2;
		}
		return Ink.isPresent(ink[pixel]) ? Ink.dye(ink[pixel]).getId() : -1;
	}

	private static int visit(BitSet out, IntPredicate passes, int pixel, int[] stack, int size) {
		if (!out.get(pixel) && passes.test(pixel)) {
			out.set(pixel);
			stack[size++] = pixel;
		}
		return size;
	}
}
