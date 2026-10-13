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
 * posts, as far as a post's reach; neighbors meet halfway, and water stops it,
 * so rivers and coasts become borders. Only land the map has explored is held.
 */
public final class Territory {
	public static final Territory NONE = new Territory(new byte[0], List.of());

	/** Which nation holds each pixel, counted from 1, or 0 for no one. */
	private final byte[] owners;
	private final List<DyeColor> colors;
	/** Tells territories apart, so maps are sent again when theirs changes; 0 for no territory. */
	private final int version;

	private Territory(byte[] owners, List<DyeColor> colors) {
		this.owners = owners;
		this.colors = colors;
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
		// Costs in tenths of a block, so diagonal steps can cost 1.4 of a straight one.
		int[] cost = new int[Ink.PIXELS];
		Arrays.fill(cost, Integer.MAX_VALUE);
		byte[] owners = new byte[Ink.PIXELS];
		PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
		int limit = reach * 10;

		for (Post post : posts) {
			int x = post.pos().getX();
			int z = post.pos().getZ();
			if (!post.dimension().equals(map.dimension) || x < minX || x >= maxX || z < minZ || z >= maxZ) {
				continue;
			}
			Integer nation = nations.get(post.design());
			if (nation == null) {
				if (colors.size() >= 255) {
					continue;
				}
				colors.add(post.color());
				nation = colors.size();
				nations.put(post.design(), nation);
			}
			// A post off the map holds land from the edge nearest to it, as far as it still reaches.
			int px = Math.clamp(MapPixels.pixelX(map, x), 0, Ink.SIZE - 1);
			int pz = Math.clamp(MapPixels.pixelZ(map, z), 0, Ink.SIZE - 1);
			int dx = Math.max(0, Math.max(MapPixels.worldX(map, px) - x, x - (MapPixels.worldX(map, px) + scale - 1)));
			int dz = Math.max(0, Math.max(MapPixels.worldZ(map, pz) - z, z - (MapPixels.worldZ(map, pz) + scale - 1)));
			int start = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz) * 10);
			int index = px + pz * Ink.SIZE;
			if (start <= limit && start < cost[index] && (start == 0 || isLand(map, index))) {
				cost[index] = start;
				owners[index] = (byte) (int) nation;
				queue.add(new long[] {start, index});
			}
		}
		if (colors.isEmpty()) {
			return NONE;
		}

		int straight = 10 * scale;
		int diagonal = 14 * scale;
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
					int step = cost[index] + (dx != 0 && dy != 0 ? diagonal : straight);
					if (step <= limit && step < cost[neighbor] && isLand(map, neighbor)) {
						cost[neighbor] = step;
						owners[neighbor] = owners[index];
						queue.add(new long[] {step, neighbor});
					}
				}
			}
		}
		// Only land the map has explored shows as held.
		boolean any = false;
		for (int i = 0; i < Ink.PIXELS; i++) {
			if (map.colors[i] == 0) {
				owners[i] = 0;
			}
			any |= owners[i] != 0;
		}
		return any ? new Territory(owners, List.copyOf(colors)) : NONE;
	}

	/** Water stops territory; so does land the map hasn't explored yet, which it can't know about. */
	private static boolean isLand(MapItemSavedData map, int index) {
		int color = map.colors[index] & 0xFF;
		return color != 0 && color >> 2 != MapColor.WATER.id;
	}
}
