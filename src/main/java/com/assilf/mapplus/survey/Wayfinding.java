package com.assilf.mapplus.survey;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.MapPlusConfig;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Wayfinding: looking through a spyglass with a compass in the other hand
 * points out the nearest biome its holder hasn't been to yet, as Adventuring
 * Time counts them: its name, how far, and which way. The search runs in the
 * background, and again once its holder has moved on. Runs on the server, so
 * it works for players without the mod too.
 */
public final class Wayfinding {
	private static final Identifier ADVENTURING_TIME = Identifier.withDefaultNamespace("adventure/adventuring_time");
	/** How far the search looks, in blocks, and how finely. */
	private static final int RADIUS = 3200;
	private static final int STEP = 32;
	private static final int VERTICAL_STEP = 64;
	/** How often the way is shown while looking, in ticks. */
	private static final int SHOW = 10;
	/** How far its holder moves, in blocks, or how long goes by, in ticks, before the search runs again. */
	private static final int MOVED = 128;
	private static final int STALE = 600;
	private static final String[] DIRECTIONS = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
	private static final String[] DIRECTION_NAMES = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

	/** What a search found: the nearest biome not yet been to, or null for none in reach. */
	private record Found(ResourceKey<Level> dimension, BlockPos from, int at, BlockPos pos, Holder<Biome> biome) {
	}

	private static final Map<UUID, Found> FOUND = new HashMap<>();
	private static final Set<UUID> SEARCHING = new HashSet<>();

	private Wayfinding() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Wayfinding::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FOUND.remove(handler.player.getUUID()));
	}

	private static void tick(MinecraftServer server) {
		if (!MapPlusConfig.get().spyglassWayfinding || server.getTickCount() % SHOW != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isScoping()) {
				continue;
			}
			InteractionHand other = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			if (player.getItemInHand(other).is(Items.COMPASS)) {
				look(server, player);
			}
		}
	}

	private static void look(MinecraftServer server, ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Found found = FOUND.get(player.getUUID());
		if (found != null && !found.dimension().equals(level.dimension())) {
			found = null;
		}
		boolean stale = found == null || found.from().distSqr(player.blockPosition()) > (double) MOVED * MOVED
				|| server.getTickCount() - found.at() > STALE;
		if (stale && !SEARCHING.contains(player.getUUID())) {
			search(server, player, level, found);
		}
		if (found != null) {
			show(player, found);
		} else {
			player.sendOverlayMessage(Component.translatableWithFallback("mapplus.wayfinding.looking", "Looking for somewhere new..."));
		}
	}

	private static void search(MinecraftServer server, ServerPlayer player, ServerLevel level, Found before) {
		AdvancementHolder adventuring = server.getAdvancements().get(ADVENTURING_TIME);
		if (adventuring == null) {
			return;
		}
		Set<String> unseen = new HashSet<>(adventuring.value().criteria().keySet());
		for (String seen : player.getAdvancements().getOrStartProgress(adventuring).getCompletedCriteria()) {
			unseen.remove(seen);
		}
		UUID id = player.getUUID();
		BlockPos from = player.blockPosition();
		SEARCHING.add(id);
		CompletableFuture.supplyAsync(() -> unseen.isEmpty() ? null
						: level.findClosestBiome3d(biome -> unseen.contains(biome.getRegisteredName()), from, RADIUS, STEP, VERTICAL_STEP))
				.whenCompleteAsync((result, error) -> {
					SEARCHING.remove(id);
					if (error != null) {
						MapPlus.LOGGER.debug("Wayfinding could not search", error);
						return;
					}
					Pair<BlockPos, Holder<Biome>> pair = result;
					Found found = new Found(level.dimension(), from, server.getTickCount(), pair == null ? null : pair.getFirst(),
							pair == null ? null : pair.getSecond());
					FOUND.put(id, found);
					ServerPlayer holder = server.getPlayerList().getPlayer(id);
					// A new way found clicks into place, as a lodestone compass does.
					if (holder != null && found.biome() != null && (before == null || before.biome() == null || !before.biome().equals(found.biome()))) {
						holder.level().playSound(null, holder.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 0.6F, 1.2F);
					}
				}, server);
	}

	private static void show(ServerPlayer player, Found found) {
		if (found.biome() == null) {
			player.sendOverlayMessage(Component.translatableWithFallback("mapplus.wayfinding.none", "Nowhere new within reach"));
			return;
		}
		String name = found.biome().getRegisteredName();
		Component biome = Component.translatable("biome." + name.replace(':', '.'));
		double dx = found.pos().getX() + 0.5 - player.getX();
		double dz = found.pos().getZ() + 0.5 - player.getZ();
		int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		// North is 0, clockwise.
		double bearing = Math.toDegrees(Math.atan2(dx, -dz));
		int way = Math.floorMod((int) Math.round(bearing / 45.0), 8);
		Component direction = Component.translatableWithFallback("mapplus.direction." + DIRECTIONS[way], DIRECTION_NAMES[way]);
		player.sendOverlayMessage(Component.translatableWithFallback("mapplus.wayfinding.found", "%s: %s blocks %s", biome, distance, direction));
	}
}
