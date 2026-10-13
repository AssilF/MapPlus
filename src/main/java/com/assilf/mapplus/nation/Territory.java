package com.assilf.mapplus.nation;

import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.survey.MapPixels;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Which nation holds each pixel of a map. Land floods out from each nation's
 * posts as far as a post's reach goes, and the land itself decides how far
 * that is: plains are easy to hold, forests, snow and slopes less so, and
 * mountains least of all, so borders follow the lie of the land. Neighbors
 * meet where their reach does, water stops it, so rivers and coasts become
 * borders, and land a nation surrounds on every side becomes its own. Land a
 * post's banner walked the bounds of is held first, whatever it is, and other
 * posts' reach stops at it. Only land the map has explored is held.
 */
public final class Territory {
	public static final Territory NONE = new Territory(new byte[0], List.of(), List.of());

	/** Which nation holds each pixel, counted from 1, or 0 for no one. */
	private final byte[] owners;
	private final List<DyeColor> colors;
	/** Each nation's design, by the number it holds pixels with. */
	private final List<String> designs;
	/** Tells territories apart, so maps are sent again when theirs changes; 0 for no territory. */
	private final int version;

	private Territory(byte[] owners, List<DyeColor> colors, List<String> designs) {
		this.owners = owners;
		this.colors = colors;
		this.designs = designs;
		this.version = owners.length == 0 ? 0 : 31 * Arrays.hashCode(owners) + colors.hashCode() | 1;
	}

	public int version() {
		return version;
	}

	public boolean isEmpty() {
		return owners.length == 0;
	}

	/** The nation holding a pixel, counted from 1, or 0 for no one. */
	public int owner(int index) {
		return owners.length == 0 ? 0 : owners[index] & 0xFF;
	}

	public DyeColor color(int owner) {
		return colors.get(owner - 1);
	}

	/**
	 * Only the land of some nations, by design: the others' land shows as no
	 * one's, though it still stopped these nations' borders where it did.
	 */
	public Territory only(java.util.Set<String> shown) {
		if (isEmpty() || shown.containsAll(designs)) {
			return this;
		}
		byte[] kept = owners.clone();
		boolean any = false;
		for (int i = 0; i < kept.length; i++) {
			int owner = kept[i] & 0xFF;
			if (owner != 0 && !shown.contains(designs.get(owner - 1))) {
				kept[i] = 0;
			}
			any |= kept[i] != 0;
		}
		return any ? new Territory(kept, colors, designs) : NONE;
	}

	/** Whether a held pixel is on its nation's edge, where a neighbor isn't held by the same nation. */
	public boolean isBorder(int index) {
		int owner = owner(index);
		if (owner == 0) {
			return false;
		}
		int x = index % Ink.SIZE;
		int y = index / Ink.SIZE;
		return x > 0 && owner(index - 1) != owner || x < Ink.SIZE - 1 && owner(index + 1) != owner
				|| y > 0 && owner(index - Ink.SIZE) != owner || y < Ink.SIZE - 1 && owner(index + Ink.SIZE) != owner;
	}

	/**
	 * Draws the territory over a rectangle of map colors, in place: borders in
	 * the nation's darker shade, and the land inside as a light pattern for
	 * players who can only be sent map colors. Unexplored pixels stay blank.
	 */
	public void overlay(int startX, int startY, int width, int height, byte[] colors, boolean insideAsPattern) {
		if (isEmpty()) {
			return;
		}
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int px = startX + x;
				int py = startY + y;
				int index = px + py * Ink.SIZE;
				int owner = owner(index);
				if (owner == 0 || colors[x + y * width] == 0) {
					continue;
				}
				MapColor color = color(owner).getMapColor();
				if (isBorder(index)) {
					colors[x + y * width] = color.getPackedId(MapColor.Brightness.LOW);
				} else if (insideAsPattern && Ink.showsAt(Ink.FAINTEST, px, py)) {
					colors[x + y * width] = color.getPackedId(MapColor.Brightness.NORMAL);
				}
			}
		}
	}

	/** Adds the territory to a map's ink, wherever there's none: borders as full ink, the land inside as the faintest tint. */
	public void addTo(byte[] ink) {
		if (isEmpty()) {
			return;
		}
		for (int i = 0; i < Ink.PIXELS; i++) {
			int owner = owner(i);
			if (owner != 0 && !Ink.isPresent(ink[i])) {
				ink[i] = Ink.encode(color(owner), false, isBorder(i) ? Ink.FULL : Ink.FAINTEST);
			}
		}
	}

	/**
	 * Works out a map's territory from the posts near it.
	 *
	 * @param reach how far a post holds land, in blocks
	 */
	public static Territory of(MapItemSavedData map, List<Post> posts, int reach) {
		int scale = MapPixels.scaleBlocks(map);
		int minX = MapPixels.worldX(map, 0) - reach;
		int minZ = MapPixels.worldZ(map, 0) - reach;
		int maxX = MapPixels.worldX(map, Ink.SIZE) + reach;
		int maxZ = MapPixels.worldZ(map, Ink.SIZE) + reach;

		Map<String, Integer> nations = new HashMap<>();
		List<DyeColor> colors = new ArrayList<>();
		List<String> designs = new ArrayList<>();
		// Costs in tenths of a block, so diagonal steps can cost 1.4 of a straight one.
		int[] cost = new int[Ink.PIXELS];
		Arrays.fill(cost, Integer.MAX_VALUE);
		byte[] owners = new byte[Ink.PIXELS];
		// Pixels held by walked bounds: no one else's reach takes them.
		boolean[] claimed = new boolean[Ink.PIXELS];
		PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
		int limit = reach * 10;

		// Walked bounds first, the oldest first, so a later claim never takes an earlier one's land.
		int mapMinX = MapPixels.worldX(map, 0);
		int mapMinZ = MapPixels.worldZ(map, 0);
		int mapMaxX = MapPixels.worldX(map, Ink.SIZE);
		int mapMaxZ = MapPixels.worldZ(map, Ink.SIZE);
		for (Post post : posts) {
			if (!post.dimension().equals(map.dimension) || !post.claim().overlaps(mapMinX, mapMinZ, mapMaxX, mapMaxZ)) {
				continue;
			}
			int nation = nation(post, nations, colors, designs);
			if (nation == 0) {
				continue;
			}
			for (int pz = 0; pz < Ink.SIZE; pz++) {
				for (int px = 0; px < Ink.SIZE; px++) {
					int index = px + pz * Ink.SIZE;
					if (!claimed[index] && post.claim().holds(MapPixels.worldX(map, px) + scale / 2, MapPixels.worldZ(map, pz) + scale / 2)) {
						claimed[index] = true;
						owners[index] = (byte) nation;
					}
				}
			}
		}

		for (Post post : posts) {
			int x = post.pos().getX();
			int z = post.pos().getZ();
			if (!post.dimension().equals(map.dimension) || x < minX || x >= maxX || z < minZ || z >= maxZ) {
				continue;
			}
			int nation = nation(post, nations, colors, designs);
			if (nation == 0) {
				continue;
			}
			// A post off the map holds land from the edge nearest to it, as far as it still reaches.
			int px = Math.clamp(MapPixels.pixelX(map, x), 0, Ink.SIZE - 1);
			int pz = Math.clamp(MapPixels.pixelZ(map, z), 0, Ink.SIZE - 1);
			int dx = Math.max(0, Math.max(MapPixels.worldX(map, px) - x, x - (MapPixels.worldX(map, px) + scale - 1)));
			int dz = Math.max(0, Math.max(MapPixels.worldZ(map, pz) - z, z - (MapPixels.worldZ(map, pz) + scale - 1)));
			int start = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz) * 10);
			int index = px + pz * Ink.SIZE;
			if (claimed[index] && (owners[index] & 0xFF) != nation) {
				continue;
			}
			if (start <= limit && start < cost[index] && (start == 0 || claimed[index] || isLand(map, index))) {
				cost[index] = start;
				owners[index] = (byte) nation;
				queue.add(new long[] {start, index});
			}
		}
		if (colors.isEmpty()) {
			return NONE;
		}

		while (!queue.isEmpty()) {
			long[] next = queue.poll();
			int index = (int) next[1];
			if (next[0] != cost[index]) {
				continue;
			}
			int x = index % Ink.SIZE;
			int y = index / Ink.SIZE;
			for (int dy = -1; dy <= 1; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					int nx = x + dx;
					int ny = y + dy;
					if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= Ink.SIZE || ny >= Ink.SIZE) {
						continue;
					}
					int neighbor = nx + ny * Ink.SIZE;
					// A nation's reach crosses its own walked bounds, water and all, and stops at anyone else's.
					if (claimed[neighbor] ? owners[neighbor] != owners[index] : !isLand(map, neighbor)) {
						continue;
					}
					// A diagonal step goes 1.4 times as far.
					int step = cost[index] + (dx != 0 && dy != 0 ? 14 : 10) * scale * toll(map, neighbor) / 10;
					if (step <= limit && step < cost[neighbor]) {
						cost[neighbor] = step;
						owners[neighbor] = owners[index];
						queue.add(new long[] {step, neighbor});
					}
				}
			}
		}
		surround(map, owners);
		// Only land the map has explored shows as held.
		boolean any = false;
		for (int i = 0; i < Ink.PIXELS; i++) {
			if (map.colors[i] == 0) {
				owners[i] = 0;
			}
			any |= owners[i] != 0;
		}
		return any ? new Territory(owners, List.copyOf(colors), List.copyOf(designs)) : NONE;
	}

	/** The number a post's nation holds pixels with, counted from 1, or 0 if there are too many nations to tell apart. */
	private static int nation(Post post, Map<String, Integer> nations, List<DyeColor> colors, List<String> designs) {
		Integer nation = nations.get(post.design());
		if (nation == null) {
			if (colors.size() >= 255) {
				return 0;
			}
			colors.add(post.color());
			designs.add(post.design());
			nation = colors.size();
			nations.put(post.design(), nation);
		}
		return nation;
	}

	/**
	 * What crossing a pixel's land costs, in tenths of a block of reach: open
	 * ground the least, then sand, forest and snow, and rock the most, with
	 * slopes (a map's lighter and darker shades) costing more again.
	 */
	private static int toll(MapItemSavedData map, int index) {
		int color = map.colors[index] & 0xFF;
		int base = color >> 2;
		int toll;
		if (base == MapColor.GRASS.id || base == MapColor.DIRT.id || base == MapColor.WOOD.id) {
			toll = 10;
		} else if (base == MapColor.SAND.id) {
			toll = 12;
		} else if (base == MapColor.PLANT.id) {
			toll = 18;
		} else if (base == MapColor.SNOW.id || base == MapColor.ICE.id) {
			toll = 16;
		} else if (base == MapColor.STONE.id || base == MapColor.DEEPSLATE.id || base == MapColor.TERRACOTTA_WHITE.id) {
			toll = 24;
		} else {
			toll = 14;
		}
		int shade = color & 3;
		return shade == 1 ? toll : toll * 13 / 10;
	}

	/** The largest stretch of land a nation takes by surrounding it. */
	private static final int MOST_SURROUNDED = 4096;

	/**
	 * Gives each stretch of unheld land that one nation surrounds on every side
	 * to that nation, as long as it doesn't run off the map's edge, where the
	 * map can't tell what's beyond.
	 */
	private static void surround(MapItemSavedData map, byte[] owners) {
		boolean[] seen = new boolean[Ink.PIXELS];
		int[] region = new int[Ink.PIXELS];
		for (int start = 0; start < Ink.PIXELS; start++) {
			if (seen[start] || owners[start] != 0 || !isLand(map, start)) {
				continue;
			}
			int size = 0;
			int surrounder = 0;
			boolean open = false;
			region[size++] = start;
			seen[start] = true;
			for (int i = 0; i < size; i++) {
				int index = region[i];
				int x = index % Ink.SIZE;
				int y = index / Ink.SIZE;
				if (x == 0 || y == 0 || x == Ink.SIZE - 1 || y == Ink.SIZE - 1) {
					open = true;
				}
				int[] neighbors = {x > 0 ? index - 1 : -1, x < Ink.SIZE - 1 ? index + 1 : -1, y > 0 ? index - Ink.SIZE : -1,
						y < Ink.SIZE - 1 ? index + Ink.SIZE : -1};
				for (int neighbor : neighbors) {
					if (neighbor < 0 || !isLand(map, neighbor)) {
						continue;
					}
					int owner = owners[neighbor] & 0xFF;
					if (owner != 0) {
						if (surrounder == 0) {
							surrounder = owner;
						} else if (surrounder != owner) {
							open = true;
						}
					} else if (!seen[neighbor]) {
						seen[neighbor] = true;
						region[size++] = neighbor;
					}
				}
			}
			if (!open && surrounder != 0 && size <= MOST_SURROUNDED) {
				for (int i = 0; i < size; i++) {
					owners[region[i]] = (byte) surrounder;
				}
			}
		}
	}

	/** Water stops territory; so does land the map hasn't explored yet, which it can't know about. */
	private static boolean isLand(MapItemSavedData map, int index) {
		int color = map.colors[index] & 0xFF;
		return color != 0 && color >> 2 != MapColor.WATER.id;
	}
}
