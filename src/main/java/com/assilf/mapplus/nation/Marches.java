package com.assilf.mapplus.nation;

import com.assilf.mapplus.MapPlusConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Walking the bounds. A named banner can be worn on the head, as a raid
 * captain wears one, and while it's worn it remembers its wearer's trail. Back
 * where the march began, or back in the nation's own land, the bounds are
 * closed; planted, the banner claims the trail and the land it closes in (see
 * {@link Claim#annex}). Runs on the server, so players without the mod can
 * march too.
 */
public final class Marches {
	/** How often marches are followed, in ticks. */
	private static final int EVERY = 5;
	/** How far apart the trail's points are, in blocks. */
	private static final int STEP = 4;
	/** A jump this far between two looks, as by a teleport, breaks the march off. */
	private static final int BREAK = 96;
	/** How close to its start a march has to come back to close. */
	private static final int CLOSE = 8;
	/** How far a march has to go before it can close. */
	private static final int LEAST_WALK = 32;
	/** How often a march's progress is shown, in ticks. */
	private static final int SHOW = 20;

	/** When each wearer was last shown their march. */
	private static final Map<UUID, Integer> SHOWN = new HashMap<>();

	private Marches() {
	}

	public static void register() {
		// Banners go on the head, as a raid captain's does.
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(item -> item instanceof BannerItem, (builder, item) -> {
			if (MapPlusConfig.get().wearableBanners) {
				builder.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
						.setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
						.setDispensable(false)
						.setSwappable(true)
						.build());
			}
		}));
		ServerTickEvents.END_SERVER_TICK.register(Marches::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SHOWN.remove(handler.player.getUUID()));
	}

	/** The bounds a worn banner is walking, and its color. */
	public record Worn(Bounds bounds, DyeColor color) {
	}

	/** The bounds a player's worn banner is walking, or null. */
	public static Worn worn(Player player) {
		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		if (!(head.getItem() instanceof BannerItem banner)) {
			return null;
		}
		Bounds bounds = Bounds.read(head);
		return bounds != null && bounds.dimension().equals(player.level().dimension()) ? new Worn(bounds, banner.getColor()) : null;
	}

	private static void tick(MinecraftServer server) {
		MapPlusConfig config = MapPlusConfig.get();
		if (!config.nations || config.boundsLength <= 0 || server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			step(player);
		}
	}

	/** Follows a player's march a step: where they are now goes on the trail of the banner they wear. */
	public static void step(Player player) {
		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		MinecraftServer server = player.level().getServer();
		if (head.getItem() instanceof BannerItem && !player.isSpectator() && server != null) {
			march(player, head, NationData.get(server), MapPlusConfig.get());
		}
	}

	private static void march(Player player, ItemStack banner, NationData data, MapPlusConfig config) {
		String name = Nations.name(banner);
		if (name.isEmpty()) {
			return;
		}
		String design = Nations.design(banner);
		int x = player.getBlockX();
		int z = player.getBlockZ();
		boolean home = Nations.holds(data, player.level().dimension(), design, x, z);
		Bounds bounds = Bounds.read(banner);
		Bounds was = bounds;
		Component nation = Component.literal(name).withStyle(style -> style.withColor(TextColor.fromRgb(((BannerItem) banner.getItem()).getColor().getTextColor())));

		if (bounds == null || !bounds.name().equals(name) || !bounds.dimension().equals(player.level().dimension())) {
			bounds = Bounds.start(player.level().dimension(), name, x, z, home);
			show(player, Component.translatableWithFallback("mapplus.bounds.start", "Walking the bounds of %s", nation));
		} else if (!bounds.closed()) {
			double step = Math.sqrt((double) (x - bounds.lastX()) * (x - bounds.lastX()) + (double) (z - bounds.lastZ()) * (z - bounds.lastZ()));
			if (step > BREAK) {
				bounds = Bounds.start(player.level().dimension(), name, x, z, home);
				show(player, Component.translatableWithFallback("mapplus.bounds.broken", "The march broke off; the bounds of %s start again here",
						nation));
			} else if (step >= STEP && bounds.walked() + step <= config.boundsLength) {
				bounds = bounds.to(x, z, (int) Math.round(step));
			}
			if (bounds.home() && !home && !bounds.out()) {
				bounds = bounds.wentOut();
			}
			if (bounds.walked() >= LEAST_WALK && bounds.count() > 3) {
				if (bounds.fromStart(x, z) <= CLOSE) {
					bounds = bounds.closedAtStart();
				} else if (bounds.home() && bounds.out() && home) {
					bounds = bounds.to(x, z, 0).closedHere();
				}
				if (bounds.closed()) {
					player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 1.0F, 1.0F);
					show(player, Component.translatableWithFallback("mapplus.bounds.closed",
							"The bounds of %s are walked: plant the banner to claim them", nation));
				}
			}
			if (!bounds.closed()) {
				progress(player, bounds, nation, x, z, config);
			}
		}
		if (!bounds.equals(was)) {
			bounds.write(banner);
		}
	}

	/** Now and then, how the march is going: how far it has gone, and how far it is back to the start. */
	private static void progress(Player player, Bounds bounds, Component nation, int x, int z, MapPlusConfig config) {
		int now = player.level().getServer().getTickCount();
		Integer shown = SHOWN.get(player.getUUID());
		if (shown != null && now - shown < SHOW) {
			return;
		}
		// Shown while surveying with a map, or coming back to the start; otherwise a worn banner is just worn.
		boolean surveying = player.getMainHandItem().is(Items.FILLED_MAP) || player.getOffhandItem().is(Items.FILLED_MAP);
		if (!surveying && bounds.fromStart(x, z) > 3 * CLOSE) {
			return;
		}
		Component message;
		if (bounds.walked() + STEP > config.boundsLength) {
			message = Component.translatableWithFallback("mapplus.bounds.full", "The banner can't remember any more: head back to close the bounds of %s",
					nation);
		} else {
			message = Component.translatableWithFallback("mapplus.bounds.progress", "Bounds of %s: %s blocks walked, %s back to the start", nation,
					bounds.walked(), (int) Math.round(bounds.fromStart(x, z)));
		}
		show(player, message);
	}

	private static void show(Player player, Component message) {
		SHOWN.put(player.getUUID(), player.level().getServer().getTickCount());
		player.sendOverlayMessage(message);
	}
}
