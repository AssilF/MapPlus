package com.assilf.mapplus.survey;

import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.ink.InkData;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Maps of the underground, and the echo.
 * <ul>
 * <li>An empty map used deep underground is a cave map: instead of the surface,
 * it draws what its holder sees around them in the caves, at the level they're
 * at: the floor they can see, and the rock walls around it. What's drawn stays
 * drawn, as on a map of the surface: walking on at another level fills in
 * only what the map hasn't shown yet, so it never turns into a scanner of
 * whatever level its holder is at now.</li>
 * <li>An amethyst shard used with a map in the other hand rings out and sketches
 * the land around onto the map at once, through rock: the surface on a
 * surface map, the caves at your level on a cave map, where it hasn't been
 * drawn yet. An echo shard rings twice as far.</li>
 * </ul>
 */
public final class CaveMaps {
	/** How much rock and soil must be overhead for a new map to be a cave map. */
	private static final int COVER = 8;
	/** Particles in the ring an echo sends out. */
	private static final int RING_PARTICLES = 48;

	private CaveMaps() {
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack shard = player.getItemInHand(hand);
			if (!(shard.is(Items.AMETHYST_SHARD) || shard.is(Items.ECHO_SHARD)) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			ItemStack map = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
			if (!map.is(Items.FILLED_MAP) || player.getCooldowns().isOnCooldown(shard)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel serverLevel) {
				ring(serverLevel, player, map, shard.is(Items.ECHO_SHARD));
				player.getCooldowns().addCooldown(shard, MapPlusConfig.get().echoCooldown);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Whether a map made here should be a cave map: deep enough under rock and soil. */
	public static boolean isUnderground(ServerLevel level, Player player) {
		if (!MapPlusConfig.get().caveMaps) {
			return false;
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
		return player.getBlockY() + COVER < surface;
	}

	/** Makes a newly made map a cave map. */
	public static void makeCaveMap(ServerLevel level, Player player, ItemStack stack) {
		MapId mapId = stack.get(DataComponents.MAP_ID);
		if (mapId == null) {
			return;
		}
		InkData.get(level.getServer()).setDepth(mapId, player.getBlockY());
		stack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("mapplus.map.cave", "Cave Map"));
	}

	/** The level a cave map draws around a holder: their head, so walls are what blocks their way. */
	private static int levelOf(Entity holder) {
		return holder.getBlockY() + 1;
	}

	/**
	 * Fills in a held cave map with what its holder can see around them, as
	 * walking fills in a map of the surface. Returns false for maps of the
	 * surface, which vanilla fills in.
	 */
	public static boolean update(ServerLevel level, Entity holder, MapItemSavedData map, MapId mapId) {
		if (mapId == null || InkData.get(level.getServer()).depth(mapId) == null) {
			return false;
		}
		if (!map.dimension.equals(level.dimension())) {
			return true;
		}
		int s = MapPixels.scaleBlocks(map);
		int reach = Math.max(1, MapPlusConfig.get().caveMapReach / s);
		// A quarter of the columns each tick, as vanilla spreads its own updates.
		int slice = (int) (level.getGameTime() & 3);
		fill(level, map, levelOf(holder), MapPixels.pixelX(map, holder.getBlockX()), MapPixels.pixelZ(map, holder.getBlockZ()), reach, slice,
				holder);
		return true;
	}

	/** Rings a shard: fills in the map all around at once, as far as the ring reaches. */
	public static void ring(ServerLevel level, Player player, ItemStack mapStack, boolean echo) {
		// A rolling peal of thunder under the chime, and a ring of sparks going out.
		level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, echo ? 0.8F : 0.45F,
				echo ? 0.7F : 1.4F);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, echo ? 0.6F : 1.2F);
		double radius = echo ? 3.0 : 2.0;
		for (int i = 0; i < RING_PARTICLES; i++) {
			double angle = i * Math.PI * 2 / RING_PARTICLES;
			level.sendParticles(echo ? ParticleTypes.SCULK_SOUL : ParticleTypes.ELECTRIC_SPARK, player.getX() + Math.cos(angle) * radius,
					player.getY() + 1.0, player.getZ() + Math.sin(angle) * radius, 1, Math.cos(angle) * 0.2, 0.0, Math.sin(angle) * 0.2, 0.15);
		}
		MapId mapId = mapStack.get(DataComponents.MAP_ID);
		MapItemSavedData map = MapItem.getSavedData(mapStack, level);
		if (mapId == null || map == null || map.locked || !map.dimension.equals(level.dimension())) {
			return;
		}
		Integer depth = InkData.get(level.getServer()).depth(mapId);
		int reach = Math.max(1, MapPlusConfig.get().echoReach * (echo ? 2 : 1) / MapPixels.scaleBlocks(map));
		int px = MapPixels.pixelX(map, player.getBlockX());
		int pz = MapPixels.pixelZ(map, player.getBlockZ());
		if (depth != null) {
			fill(level, map, levelOf(player), px, pz, reach, -1, null);
		} else {
			MapPixels.ChunkLookup loaded = MapPixels.ChunkLookup.loaded(level);
			for (int z = Math.max(0, pz - reach); z <= Math.min(127, pz + reach); z++) {
				for (int x = Math.max(0, px - reach); x <= Math.min(127, px + reach); x++) {
					if ((x - px) * (x - px) + (z - pz) * (z - pz) > reach * reach) {
						continue;
					}
					double north = z > 0 ? MapPixels.height(level, loaded, map, x, z - 1) : Double.NaN;
					int color = MapPixels.color(level, loaded, map, x, z, north);
					if (color >= 0) {
						map.setColor(x, z, (byte) color);
					}
				}
			}
		}
	}

	/**
	 * Fills a cave map's blank pixels in a circle; with a slice from 0 to 3, only
	 * that quarter of the columns. With a viewer, only what they can see from
	 * where they stand; without one, everything, as an echo hears through rock.
	 * A pixel already drawn keeps what was first seen there.
	 */
	private static void fill(ServerLevel level, MapItemSavedData map, int depth, int px, int pz, int reach, int slice, Entity viewer) {
		MapPixels.ChunkLookup loaded = MapPixels.ChunkLookup.loaded(level);
		int s = MapPixels.scaleBlocks(map);
		Vec3 eye = viewer == null ? null : viewer.getEyePosition();
		for (int x = Math.max(0, px - reach); x <= Math.min(127, px + reach); x++) {
			if (slice >= 0 && (x & 3) != slice) {
				continue;
			}
			for (int z = Math.max(0, pz - reach); z <= Math.min(127, pz + reach); z++) {
				if ((x - px) * (x - px) + (z - pz) * (z - pz) > reach * reach || map.colors[x + z * 128] != 0) {
					continue;
				}
				if (eye != null) {
					BlockPos target = new BlockPos(MapPixels.worldX(map, x) + s / 2, depth, MapPixels.worldZ(map, z) + s / 2);
					BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(target), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
							viewer));
					if (hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals(target)) {
						continue;
					}
				}
				int color = MapPixels.caveColor(level, loaded, map, x, z, depth);
				if (color >= 0) {
					map.setColor(x, z, (byte) color);
				}
			}
		}
	}
}
