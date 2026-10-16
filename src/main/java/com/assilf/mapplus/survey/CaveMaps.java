package com.assilf.mapplus.survey;

import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.ink.InkData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

	/** An echo of a ring still to be heard: where, when, how loud and how high. */
	private record Echo(ServerLevel level, Vec3 at, int tick, float volume, float pitch, double radius, boolean deep) {
	}

	private static final List<Echo> ECHOES = new ArrayList<>();
	/** When the ring's echoes come back, in ticks after it, and how high each answers. */
	private static final int[] ECHO_TICKS = {3, 7, 12, 18, 25};
	private static final float[] ECHO_PITCHES = {1.5F, 1.26F, 1.68F, 1.12F, 1.33F};

	private CaveMaps() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CaveMaps::echoes);
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
		// The shard rings: a chord of amethyst, the crystal humming under it. Its echoes answer from all around
		// over the next second, softer each time, as rings of sparks go out; an echo shard rings deeper, with the
		// rumble of the deep dark under it.
		float base = echo ? 0.5F : 1.0F;
		Vec3 at = player.position().add(0, 1.0, 0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, base);
		for (float note : new float[] {1.0F, 1.26F, 1.5F}) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, base * note);
		}
		if (echo) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.25F, 0.5F);
		}
		int now = level.getServer().getTickCount();
		double reachBlocks = MapPlusConfig.get().echoReach * (echo ? 2 : 1);
		for (int i = 0; i < ECHO_TICKS.length; i++) {
			float volume = 0.9F * (float) Math.pow(0.7, i);
			ECHOES.add(new Echo(level, at, now + ECHO_TICKS[i], volume, base * ECHO_PITCHES[i], reachBlocks * (i + 1) / ECHO_TICKS.length, echo));
		}
		sparks(level, at, 1.5, echo);
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

	/** Plays the echoes that are due: each from a point out on its ring, so they come back from all around. */
	private static void echoes(MinecraftServer server) {
		if (ECHOES.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		Iterator<Echo> due = ECHOES.iterator();
		while (due.hasNext()) {
			Echo echo = due.next();
			if (echo.tick() > now) {
				continue;
			}
			due.remove();
			double angle = echo.level().getRandom().nextDouble() * Math.PI * 2;
			// Close enough to be heard, far enough to come from somewhere.
			double away = Math.min(echo.radius(), 12.0);
			Vec3 from = echo.at().add(Math.cos(angle) * away, 0, Math.sin(angle) * away);
			echo.level().playSound(null, from.x, from.y, from.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, echo.volume(), echo.pitch());
			sparks(echo.level(), echo.at(), Math.min(echo.radius(), 16.0), echo.deep());
		}
	}

	/** A ring of sparks going out (souls for an echo shard). */
	private static void sparks(ServerLevel level, Vec3 at, double radius, boolean deep) {
		for (int i = 0; i < RING_PARTICLES; i++) {
			double angle = i * Math.PI * 2 / RING_PARTICLES;
			level.sendParticles(deep ? ParticleTypes.SCULK_SOUL : ParticleTypes.ELECTRIC_SPARK, at.x + Math.cos(angle) * radius, at.y,
					at.z + Math.sin(angle) * radius, 1, Math.cos(angle) * 0.2, 0.0, Math.sin(angle) * 0.2, 0.15);
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
