package com.assilf.mapplus.nation;

import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.survey.MapPixels;
import com.google.common.collect.MapMaker;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The bounds a worn banner has walked: the trail of its wearer since they put
 * it on, kept on the banner itself. Planted, the banner claims the trail and
 * whatever it closes in for its nation.
 *
 * @param name   the banner's name when the march began; a new name starts a new march
 * @param points the trail, as block x and z pairs
 * @param walked how far the trail runs, in blocks
 * @param closed whether the trail has come back to its start, or into the nation's own land
 * @param home   whether the march began in the nation's own land
 * @param out    whether it has left the nation's own land since
 */
public record Bounds(ResourceKey<Level> dimension, String name, int[] points, int walked, boolean closed, boolean home, boolean out) {
	public static final String KEY = "mapplus:bounds";

	public static final Codec<Bounds> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Bounds::dimension),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Bounds::name),
			Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).fieldOf("trail").forGetter(Bounds::points),
			Codec.INT.optionalFieldOf("walked", 0).forGetter(Bounds::walked),
			Codec.BOOL.optionalFieldOf("closed", false).forGetter(Bounds::closed),
			Codec.BOOL.optionalFieldOf("home", false).forGetter(Bounds::home),
			Codec.BOOL.optionalFieldOf("out", false).forGetter(Bounds::out)
	).apply(instance, Bounds::new));

	public static Bounds start(ResourceKey<Level> dimension, String name, int x, int z, boolean home) {
		return new Bounds(dimension, name, new int[] {x, z}, 0, false, home, false);
	}

	private static final Map<CustomData, Optional<Bounds>> PARSED = new MapMaker().weakKeys().makeMap();

	/** The bounds a banner has walked, or null. */
	public static Bounds read(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		return PARSED.computeIfAbsent(data, d -> {
			Tag tag = d.copyTag().get(KEY);
			return tag == null ? Optional.empty() : CODEC.parse(NbtOps.INSTANCE, tag).result();
		}).orElse(null);
	}

	public void write(ItemStack stack) {
		Tag tag = CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(KEY, tag));
	}

	public static void clear(ItemStack stack) {
		if (read(stack) != null) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.remove(KEY));
		}
	}

	public int count() {
		return points.length / 2;
	}

	public int x(int i) {
		return points[2 * i];
	}

	public int z(int i) {
		return points[2 * i + 1];
	}

	public int lastX() {
		return x(count() - 1);
	}

	public int lastZ() {
		return z(count() - 1);
	}

	/** The march, a step further. */
	public Bounds to(int x, int z, int step) {
		int[] longer = Arrays.copyOf(points, points.length + 2);
		longer[points.length] = x;
		longer[points.length + 1] = z;
		return new Bounds(dimension, name, longer, walked + step, closed, home, out);
	}

	public Bounds wentOut() {
		return new Bounds(dimension, name, points, walked, closed, home, true);
	}

	/** The march, closed: back at its start, the trail is joined up. */
	public Bounds closedAtStart() {
		return to(x(0), z(0), 0).closedHere();
	}

	/** The march, closed where it is: back in the nation's own land. */
	public Bounds closedHere() {
		return new Bounds(dimension, name, points, walked, true, home, out);
	}

	/** How far a spot is from where the march began. */
	public double fromStart(int x, int z) {
		return Math.sqrt((double) (x - x(0)) * (x - x(0)) + (double) (z - z(0)) * (z - z(0)));
	}

	/** Tells trails apart, so maps showing them are sent again when they change. */
	public int version() {
		return 31 * Arrays.hashCode(points) + (closed ? 1 : 0) | 1;
	}

	/**
	 * The map pixels the trail crosses, dashed like a route on a treasure map.
	 * Steps that fall off the map are left out.
	 */
	public BitSet pixels(MapItemSavedData map) {
		BitSet pixels = new BitSet(Ink.PIXELS);
		if (!map.dimension.equals(dimension)) {
			return pixels;
		}
		int dash = 0;
		for (int i = 0; i + 1 < count(); i++) {
			int x0 = MapPixels.pixelX(map, x(i));
			int y0 = MapPixels.pixelZ(map, z(i));
			int x1 = MapPixels.pixelX(map, x(i + 1));
			int y1 = MapPixels.pixelZ(map, z(i + 1));
			if (Math.max(x0, x1) < 0 || Math.max(y0, y1) < 0 || Math.min(x0, x1) >= Ink.SIZE || Math.min(y0, y1) >= Ink.SIZE) {
				continue;
			}
			int dx = Math.abs(x1 - x0);
			int dy = -Math.abs(y1 - y0);
			int sx = x0 < x1 ? 1 : -1;
			int sy = y0 < y1 ? 1 : -1;
			int error = dx + dy;
			while (true) {
				if (x0 >= 0 && y0 >= 0 && x0 < Ink.SIZE && y0 < Ink.SIZE && dash % 4 < 3) {
					pixels.set(x0 + y0 * Ink.SIZE);
				}
				if (x0 == x1 && y0 == y1) {
					break;
				}
				dash++;
				int twice = 2 * error;
				if (twice >= dy) {
					error += dy;
					x0 += sx;
				}
				if (twice <= dx) {
					error += dx;
					y0 += sy;
				}
			}
		}
		return pixels;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Bounds bounds && dimension.equals(bounds.dimension) && name.equals(bounds.name)
				&& Arrays.equals(points, bounds.points) && walked == bounds.walked && closed == bounds.closed && home == bounds.home
				&& out == bounds.out;
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(points) + name.hashCode();
	}
}
