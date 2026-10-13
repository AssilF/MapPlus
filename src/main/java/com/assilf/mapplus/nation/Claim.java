package com.assilf.mapplus.nation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

/**
 * Land a border post holds because its banner walked the bounds of it: the
 * walked trail, a few blocks wide, and everything it closes in. Kept as cells of
 * 2×2 blocks, in tiles of 16×16 cells.
 */
public final class Claim {
	/** A cell's side, in blocks. */
	public static final int CELL = 2;
	private static final int TILE = 16;
	/** The widest stretch of land one march can take, in cells: a march as long as a banner can remember, walked straight. */
	private static final int MOST_CELLS_ACROSS = 2112;

	public static final Claim NONE = new Claim(Map.of());

	private record Tile(int x, int z, long[] bits) {
		static final Codec<Tile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("x").forGetter(Tile::x),
				Codec.INT.fieldOf("z").forGetter(Tile::z),
				Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream).fieldOf("cells").forGetter(Tile::bits)
		).apply(instance, Tile::new));
	}

	public static final Codec<Claim> CODEC = Tile.CODEC.listOf().xmap(Claim::fromTiles, Claim::toTiles);

	private final Map<Long, long[]> tiles;
	private final int cells;
	/** The blocks the claim spans, for a quick look at whether it reaches a map. */
	private final int minX;
	private final int minZ;
	private final int maxX;
	private final int maxZ;

	private Claim(Map<Long, long[]> tiles) {
		this.tiles = tiles;
		int count = 0;
		int lowX = Integer.MAX_VALUE;
		int lowZ = Integer.MAX_VALUE;
		int highX = Integer.MIN_VALUE;
		int highZ = Integer.MIN_VALUE;
		for (Map.Entry<Long, long[]> tile : tiles.entrySet()) {
			for (long word : tile.getValue()) {
				count += Long.bitCount(word);
			}
			int tx = (int) (tile.getKey() >> 32);
			int tz = (int) (long) tile.getKey();
			lowX = Math.min(lowX, tx * TILE * CELL);
			lowZ = Math.min(lowZ, tz * TILE * CELL);
			highX = Math.max(highX, (tx + 1) * TILE * CELL);
			highZ = Math.max(highZ, (tz + 1) * TILE * CELL);
		}
		this.cells = count;
		this.minX = lowX;
		this.minZ = lowZ;
		this.maxX = highX;
		this.maxZ = highZ;
	}

	private static long key(int tileX, int tileZ) {
		return (long) tileX << 32 | tileZ & 0xFFFFFFFFL;
	}

	private static Claim fromTiles(List<Tile> list) {
		Map<Long, long[]> tiles = new HashMap<>();
		for (Tile tile : list) {
			if (tile.bits().length == TILE * TILE / 64) {
				tiles.put(key(tile.x(), tile.z()), tile.bits().clone());
			}
		}
		return tiles.isEmpty() ? NONE : new Claim(tiles);
	}

	private List<Tile> toTiles() {
		List<Tile> list = new ArrayList<>();
		for (Map.Entry<Long, long[]> tile : tiles.entrySet()) {
			list.add(new Tile((int) (tile.getKey() >> 32), (int) (long) tile.getKey(), tile.getValue()));
		}
		return list;
	}

	public boolean isEmpty() {
		return cells == 0;
	}

	/** The land held, in square blocks. */
	public int area() {
		return cells * CELL * CELL;
	}

	/** Whether the claim reaches into a stretch of blocks, ends excluded. */
	public boolean overlaps(int fromX, int fromZ, int toX, int toZ) {
		return !isEmpty() && minX < toX && fromX < maxX && minZ < toZ && fromZ < maxZ;
	}

	/** Whether the claim holds a block column. */
	public boolean holds(int x, int z) {
		if (isEmpty()) {
			return false;
		}
		int cellX = Math.floorDiv(x, CELL);
		int cellZ = Math.floorDiv(z, CELL);
		long[] bits = tiles.get(key(Math.floorDiv(cellX, TILE), Math.floorDiv(cellZ, TILE)));
		if (bits == null) {
			return false;
		}
		int index = Math.floorMod(cellX, TILE) + Math.floorMod(cellZ, TILE) * TILE;
		return (bits[index >> 6] >>> (index & 63) & 1) != 0;
	}

	/** Tells whether a block column is some nation's land. */
	@FunctionalInterface
	public interface Land {
		boolean test(int x, int z);
	}

	/**
	 * The land a walked trail takes: the trail itself, a few blocks wide, and
	 * whatever it closes in, together with the nation's own land. A loop back to
	 * where the march started, or back into the nation's own land, closes land
	 * in. Land another nation holds is never taken, and doesn't close anything
	 * in.
	 *
	 * @param points the trail, as block x and z pairs
	 * @param own    the marching nation's own land
	 * @param taken  other nations' land
	 */
	public static Claim annex(int[] points, Land own, Land taken) {
		int count = points.length / 2;
		if (count == 0) {
			return NONE;
		}
		int margin = 3;
		int lowX = Integer.MAX_VALUE;
		int lowZ = Integer.MAX_VALUE;
		int highX = Integer.MIN_VALUE;
		int highZ = Integer.MIN_VALUE;
		for (int i = 0; i < count; i++) {
			lowX = Math.min(lowX, Math.floorDiv(points[2 * i], CELL));
			lowZ = Math.min(lowZ, Math.floorDiv(points[2 * i + 1], CELL));
			highX = Math.max(highX, Math.floorDiv(points[2 * i], CELL));
			highZ = Math.max(highZ, Math.floorDiv(points[2 * i + 1], CELL));
		}
		int originX = lowX - margin;
		int originZ = lowZ - margin;
		int width = highX - lowX + 1 + 2 * margin;
		int height = highZ - lowZ + 1 + 2 * margin;
		if (width > MOST_CELLS_ACROSS || height > MOST_CELLS_ACROSS) {
			return NONE;
		}

		final byte open = 0;
		final byte trail = 1;
		final byte home = 2;
		final byte foreign = 3;
		byte[] grid = new byte[width * height];
		for (int z = 0; z < height; z++) {
			for (int x = 0; x < width; x++) {
				int blockX = (originX + x) * CELL + CELL / 2;
				int blockZ = (originZ + z) * CELL + CELL / 2;
				if (own.test(blockX, blockZ)) {
					grid[x + z * width] = home;
				} else if (taken.test(blockX, blockZ)) {
					grid[x + z * width] = foreign;
				}
			}
		}

		// The trail, widened by a cell on each side so small gaps in it still close.
		for (int i = 0; i < count; i++) {
			int x0 = Math.floorDiv(points[2 * i], CELL) - originX;
			int z0 = Math.floorDiv(points[2 * i + 1], CELL) - originZ;
			int j = Math.min(i + 1, count - 1);
			int x1 = Math.floorDiv(points[2 * j], CELL) - originX;
			int z1 = Math.floorDiv(points[2 * j + 1], CELL) - originZ;
			int dx = Math.abs(x1 - x0);
			int dz = -Math.abs(z1 - z0);
			int sx = x0 < x1 ? 1 : -1;
			int sz = z0 < z1 ? 1 : -1;
			int error = dx + dz;
			while (true) {
				for (int oz = -1; oz <= 1; oz++) {
					for (int ox = -1; ox <= 1; ox++) {
						int cell = x0 + ox + (z0 + oz) * width;
						if (grid[cell] == open) {
							grid[cell] = trail;
						}
					}
				}
				if (x0 == x1 && z0 == z1) {
					break;
				}
				int twice = 2 * error;
				if (twice >= dz) {
					error += dz;
					x0 += sx;
				}
				if (twice <= dx) {
					error += dx;
					z0 += sz;
				}
			}
		}

		// What can be reached from outside without crossing the trail or the nation's own land isn't closed in.
		boolean[] outside = new boolean[width * height];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int x = 0; x < width; x++) {
			for (int z : new int[] {0, height - 1}) {
				reach(grid, outside, queue, x + z * width);
			}
		}
		for (int z = 0; z < height; z++) {
			for (int x : new int[] {0, width - 1}) {
				reach(grid, outside, queue, x + z * width);
			}
		}
		while (!queue.isEmpty()) {
			int cell = queue.poll();
			int x = cell % width;
			int z = cell / width;
			if (x > 0) {
				reach(grid, outside, queue, cell - 1);
			}
			if (x < width - 1) {
				reach(grid, outside, queue, cell + 1);
			}
			if (z > 0) {
				reach(grid, outside, queue, cell - width);
			}
			if (z < height - 1) {
				reach(grid, outside, queue, cell + width);
			}
		}

		Map<Long, long[]> tiles = new HashMap<>();
		for (int z = 0; z < height; z++) {
			for (int x = 0; x < width; x++) {
				byte kind = grid[x + z * width];
				if (kind == trail || kind == open && !outside[x + z * width]) {
					int cellX = originX + x;
					int cellZ = originZ + z;
					long[] bits = tiles.computeIfAbsent(key(Math.floorDiv(cellX, TILE), Math.floorDiv(cellZ, TILE)),
							k -> new long[TILE * TILE / 64]);
					int index = Math.floorMod(cellX, TILE) + Math.floorMod(cellZ, TILE) * TILE;
					bits[index >> 6] |= 1L << (index & 63);
				}
			}
		}
		return tiles.isEmpty() ? NONE : new Claim(tiles);
	}

	private static void reach(byte[] grid, boolean[] outside, ArrayDeque<Integer> queue, int cell) {
		// Open land and other nations' land let the outside in; the trail and the nation's own land don't.
		if (!outside[cell] && (grid[cell] == 0 || grid[cell] == 3)) {
			outside[cell] = true;
			queue.add(cell);
		}
	}
}
