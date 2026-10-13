package com.assilf.mapplus.survey;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Computes map pixels the same way vanilla maps do when you walk around with
 * them, so surveyed pixels look exactly like explored ones.
 */
public final class MapPixels {
	/** Columns sampled per side of a pixel at most; large scales are sampled sparsely. */
	private static final int MAX_SAMPLES_PER_SIDE = 4;

	private MapPixels() {
	}

	public static int scaleBlocks(MapItemSavedData map) {
		return 1 << map.scale;
	}

	/** World X of the north-west column of a pixel. */
	public static int worldX(MapItemSavedData map, int px) {
		int s = scaleBlocks(map);
		return (map.centerX / s + px - 64) * s;
	}

	public static int worldZ(MapItemSavedData map, int pz) {
		int s = scaleBlocks(map);
		return (map.centerZ / s + pz - 64) * s;
	}

	public static int pixelX(MapItemSavedData map, int x) {
		int s = scaleBlocks(map);
		return Math.floorDiv(x, s) - map.centerX / s + 64;
	}

	public static int pixelZ(MapItemSavedData map, int z) {
		int s = scaleBlocks(map);
		return Math.floorDiv(z, s) - map.centerZ / s + 64;
	}

	/** Finds a chunk to read from, or returns null if it isn't available. */
	@FunctionalInterface
	public interface ChunkLookup {
		ChunkAccess get(int chunkX, int chunkZ);

		static ChunkLookup loaded(ServerLevel level) {
			return (chunkX, chunkZ) -> level.getChunkSource().getChunkNow(chunkX, chunkZ);
		}
	}

	/**
	 * Average surface height of a pixel, or NaN if its chunk isn't available.
	 */
	public static double height(ServerLevel level, ChunkLookup chunks, MapItemSavedData map, int px, int pz) {
		Sample sample = sample(level, chunks, map, px, pz, false);
		return sample == null ? Double.NaN : sample.height;
	}

	/**
	 * The packed map color for a pixel, or -1 if its chunk isn't available.
	 *
	 * @param northHeight average height of the pixel to the north, or NaN if unknown
	 */
	public static int color(ServerLevel level, ChunkLookup chunks, MapItemSavedData map, int px, int pz, double northHeight) {
		Sample sample = sample(level, chunks, map, px, pz, true);
		if (sample == null) {
			return -1;
		}
		int s = scaleBlocks(map);
		MapColor.Brightness brightness;
		if (sample.color == MapColor.WATER) {
			double shade = sample.waterDepth * 0.1 + ((px + pz) & 1) * 0.2;
			brightness = shade < 0.5 ? MapColor.Brightness.HIGH : shade > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
		} else {
			double north = Double.isNaN(northHeight) ? sample.height : northHeight;
			double shade = (sample.height - north) * 4.0 / (s + 4) + (((px + pz) & 1) - 0.5) * 0.4;
			brightness = shade > 0.6 ? MapColor.Brightness.HIGH : shade < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
		}
		return sample.color.getPackedId(brightness) & 0xFF;
	}

	private record Sample(double height, MapColor color, int waterDepth) {
	}

	private static Sample sample(ServerLevel level, ChunkLookup chunks, MapItemSavedData map, int px, int pz, boolean wantColor) {
		int s = scaleBlocks(map);
		int x0 = worldX(map, px);
		int z0 = worldZ(map, pz);
		int samples = Math.min(s, MAX_SAMPLES_PER_SIDE);
		int step = s / samples;

		ChunkAccess chunk = chunks.get(x0 >> 4, z0 >> 4);
		if (chunk == null) {
			return null;
		}

		Multiset<MapColor> colors = LinkedHashMultiset.create();
		double height = 0.0;
		int waterDepth = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();

		for (int dx = 0; dx < samples; dx++) {
			for (int dz = 0; dz < samples; dz++) {
				int x = x0 + dx * step;
				int z = z0 + dz * step;
				if (chunk.getPos().x() != (x >> 4) || chunk.getPos().z() != (z >> 4)) {
					chunk = chunks.get(x >> 4, z >> 4);
					if (chunk == null) {
						return null;
					}
				}

				pos.set(x, 0, z);
				int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
				BlockState state;
				if (y <= minY + 1) {
					state = Blocks.BEDROCK.defaultBlockState();
				} else {
					do {
						y--;
						pos.setY(y);
						state = chunk.getBlockState(pos);
					} while (state.getMapColor(level, pos) == MapColor.NONE && y > minY);

					if (wantColor && y > minY && !state.getFluidState().isEmpty()) {
						int depthY = y - 1;
						below.set(pos);
						BlockState belowState;
						do {
							below.setY(depthY--);
							belowState = chunk.getBlockState(below);
							waterDepth++;
						} while (depthY > minY && !belowState.getFluidState().isEmpty());
						state = surfaceOfFluid(level, state, pos);
					}
				}

				height += (double) y / (samples * samples);
				if (wantColor) {
					colors.add(state.getMapColor(level, pos));
				}
			}
		}

		waterDepth /= samples * samples;
		MapColor color = wantColor
				? Iterables.getFirst(Multisets.copyHighestCountFirst(colors), MapColor.NONE)
				: MapColor.NONE;
		return new Sample(height, color, waterDepth);
	}

	private static BlockState surfaceOfFluid(ServerLevel level, BlockState state, BlockPos pos) {
		FluidState fluid = state.getFluidState();
		return !fluid.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluid.createLegacyBlock() : state;
	}

	/** How far below a cave map's level its floor is looked for, in blocks. */
	private static final int CAVE_FLOOR_DEPTH = 24;

	/**
	 * The packed map color for a pixel of a cave map, which shows one level of
	 * the world as a floor plan: the floor under open space, shaded darker the
	 * further down it is, and rock as the darkest shade of itself. Returns -1 if
	 * the pixel's chunk isn't available.
	 */
	public static int caveColor(ServerLevel level, ChunkLookup chunks, MapItemSavedData map, int px, int pz, int depth) {
		int s = scaleBlocks(map);
		int x = worldX(map, px) + s / 2;
		int z = worldZ(map, pz) + s / 2;
		ChunkAccess chunk = chunks.get(x >> 4, z >> 4);
		if (chunk == null) {
			return -1;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, depth, z);
		BlockState state = chunk.getBlockState(pos);
		MapColor color = state.getMapColor(level, pos);
		if (color != MapColor.NONE && state.getFluidState().isEmpty() && state.blocksMotion()) {
			return color.getPackedId(MapColor.Brightness.LOWEST) & 0xFF;
		}
		int bottom = Math.max(level.getMinY(), depth - CAVE_FLOOR_DEPTH);
		for (int y = depth; y >= bottom; y--) {
			pos.setY(y);
			state = chunk.getBlockState(pos);
			color = state.getMapColor(level, pos);
			if (color == MapColor.NONE) {
				continue;
			}
			if (!state.getFluidState().isEmpty()) {
				state = surfaceOfFluid(level, state, pos);
				color = state.getMapColor(level, pos);
			}
			int drop = depth - y;
			MapColor.Brightness brightness = drop <= 1 ? MapColor.Brightness.HIGH : drop <= 4 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
			return color.getPackedId(brightness) & 0xFF;
		}
		// A drop too deep to see the bottom of.
		return MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOWEST) & 0xFF;
	}

	public static ChunkPos chunkOfPixel(MapItemSavedData map, int px, int pz) {
		return new ChunkPos(worldX(map, px) >> 4, worldZ(map, pz) >> 4);
	}
}
