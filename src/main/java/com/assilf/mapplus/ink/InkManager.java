package com.assilf.mapplus.ink;

import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.mixin.MapItemSavedDataAccessor;
import com.assilf.mapplus.net.InkSealPayload;
import com.assilf.mapplus.net.InkStatePayload;
import com.assilf.mapplus.net.InkStrokePayload;
import com.assilf.mapplus.net.OpenInkPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.function.Predicate;

/**
 * Server side of inking: validates requests, charges ink and updates map ink.
 */
public final class InkManager {
	private InkManager() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(OpenInkPayload.TYPE, (payload, context) ->
				handleOpen(context.player(), new MapId(payload.mapId())));
		ServerPlayNetworking.registerGlobalReceiver(InkStrokePayload.TYPE, (payload, context) ->
				handleStroke(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(InkSealPayload.TYPE, (payload, context) ->
				handleSeal(context.player(), new MapId(payload.mapId()), payload.seal()));
	}

	/**
	 * The map the player is inking: the map in the input slot of their open
	 * cartography table, if it matches the requested id.
	 */
	private static MapItemSavedData inkableMap(ServerPlayer player, MapId mapId) {
		if (!(player.containerMenu instanceof CartographyTableMenu menu)) {
			return null;
		}
		ItemStack stack = menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem();
		if (!stack.is(Items.FILLED_MAP) || !mapId.equals(stack.get(DataComponents.MAP_ID))) {
			return null;
		}
		return player.level().getServer().overworld().getMapData(mapId);
	}

	private static void handleOpen(ServerPlayer player, MapId mapId) {
		MapItemSavedData map = inkableMap(player, mapId);
		if (map == null) {
			ServerPlayNetworking.send(player, InkStatePayload.closed(mapId.id()));
			return;
		}
		sendState(player, mapId, map);
	}

	private static void handleStroke(ServerPlayer player, InkStrokePayload payload) {
		MapId mapId = new MapId(payload.mapId());
		MapItemSavedData map = inkableMap(player, mapId);
		if (map == null) {
			ServerPlayNetworking.send(player, InkStatePayload.closed(mapId.id()));
			return;
		}

		InkData data = InkData.get(player.level().getServer());
		InkLayer layer = data.getOrCreate(mapId);
		if (layer.isSealed()) {
			sendState(player, mapId, map);
			return;
		}

		boolean erase = payload.dye() == InkStrokePayload.ERASE;
		if (!erase && (payload.dye() < 0 || payload.dye() >= InkSupply.DYES)) {
			return;
		}
		DyeColor dye = erase ? null : DyeColor.byId(payload.dye());
		boolean glow = !erase && payload.glow();
		byte target = erase ? Ink.NONE : Ink.encode(dye, glow);

		boolean free = player.isCreative() && MapPlusConfig.get().creativeInkIsFree;
		Wallet wallet = new Wallet(player, free);

		int changed = 0;
		for (int i = 0; i < Ink.PIXELS; i++) {
			if (!payload.isSet(i) || layer.get(i) == target) {
				continue;
			}
			if (erase) {
				if (!wallet.spendWater()) {
					break;
				}
			} else if (!wallet.spendInk(dye, glow)) {
				break;
			}
			layer.set(i, target);
			changed++;
		}
		wallet.save();

		if (changed > 0) {
			data.setDirty();
			refreshMap(map);
			player.level().playSound(null, player.blockPosition(),
					erase ? SoundEvents.BUCKET_EMPTY : SoundEvents.BOOK_PAGE_TURN,
					SoundSource.PLAYERS, 0.4F, 1.0F);
		}
		sendState(player, mapId, map);
	}

	private static void handleSeal(ServerPlayer player, MapId mapId, boolean seal) {
		MapItemSavedData map = inkableMap(player, mapId);
		if (map == null) {
			ServerPlayNetworking.send(player, InkStatePayload.closed(mapId.id()));
			return;
		}

		InkData data = InkData.get(player.level().getServer());
		InkLayer layer = data.getOrCreate(mapId);
		if (layer.isSealed() == seal) {
			sendState(player, mapId, map);
			return;
		}

		boolean free = player.isCreative();
		if (seal) {
			if (!free && !consumeOne(player.getInventory(), stack -> stack.is(Items.HONEYCOMB))) {
				sendState(player, mapId, map);
				return;
			}
			player.level().playSound(null, player.blockPosition(), SoundEvents.HONEYCOMB_WAX_ON, SoundSource.PLAYERS, 1.0F, 1.0F);
		} else {
			if (!free && !damageAxe(player)) {
				sendState(player, mapId, map);
				return;
			}
			player.level().playSound(null, player.blockPosition(), SoundEvents.AXE_WAX_OFF.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
		}

		layer.setSealed(seal);
		data.setDirty();
		sendState(player, mapId, map);
	}

	/**
	 * Washes all ink off a map, as when it is dipped into a water cauldron.
	 *
	 * @return whether there was ink to wash off
	 */
	public static boolean wash(MinecraftServer server, MapId mapId) {
		InkData data = InkData.get(server);
		InkLayer layer = data.find(mapId);
		if (layer == null || layer.isSealed() || layer.isEmpty()) {
			return false;
		}
		layer.clear();
		data.setDirty();

		MapItemSavedData map = server.overworld().getMapData(mapId);
		if (map != null) {
			refreshMap(map);
		}
		return true;
	}

	/** Makes every player who sees this map receive all of its pixels again. */
	public static void refreshMap(MapItemSavedData map) {
		MapItemSavedDataAccessor accessor = (MapItemSavedDataAccessor) map;
		accessor.mapplus$setColorsDirty(0, 0);
		accessor.mapplus$setColorsDirty(Ink.SIZE - 1, Ink.SIZE - 1);
	}

	private static void sendState(ServerPlayer player, MapId mapId, MapItemSavedData map) {
		InkLayer layer = InkData.get(player.level().getServer()).find(mapId);
		byte[] ink = layer == null ? new byte[Ink.PIXELS] : layer.pixels().clone();
		boolean sealed = layer != null && layer.isSealed();
		InkSupply supply = player.getAttachedOrElse(InkSupply.ATTACHMENT, InkSupply.EMPTY);
		ServerPlayNetworking.send(player, new InkStatePayload(mapId.id(), true, sealed, map.colors.clone(), ink, supply));
	}

	private static boolean consumeOne(Inventory inventory, Predicate<ItemStack> matches) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && matches.test(stack)) {
				stack.shrink(1);
				inventory.setChanged();
				return true;
			}
		}
		return false;
	}

	private static boolean damageAxe(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(ItemTags.AXES)) {
				stack.hurtAndBreak(1, player.level(), player, item -> {
				});
				inventory.setChanged();
				return true;
			}
		}
		return false;
	}

	/**
	 * Pays for ink from the player's loaded supply, loading more from their
	 * inventory when it runs out.
	 */
	private static final class Wallet {
		private final ServerPlayer player;
		private final boolean free;
		private InkSupply supply;

		Wallet(ServerPlayer player, boolean free) {
			this.player = player;
			this.free = free;
			this.supply = player.getAttachedOrElse(InkSupply.ATTACHMENT, InkSupply.EMPTY);
		}

		boolean spendInk(DyeColor dye, boolean glow) {
			if (free) {
				return true;
			}
			if (glow && supply.glow() <= 0) {
				if (!consumeOne(player.getInventory(), InkItems::isGlowInk)) {
					return false;
				}
				supply = supply.withGlow(supply.glow() + MapPlusConfig.get().inkPerItem);
			}
			if (supply.dye(dye) <= 0) {
				if (!consumeOne(player.getInventory(), stack -> InkItems.isInkFor(stack, dye))) {
					return false;
				}
				supply = supply.withDye(dye, supply.dye(dye) + MapPlusConfig.get().inkPerItem);
			}
			supply = supply.withDye(dye, supply.dye(dye) - 1);
			if (glow) {
				supply = supply.withGlow(supply.glow() - 1);
			}
			return true;
		}

		boolean spendWater() {
			if (free) {
				return true;
			}
			if (supply.water() <= 0) {
				if (!useWaterBottle()) {
					return false;
				}
				supply = supply.withWater(supply.water() + MapPlusConfig.get().waterPerBottle);
			}
			supply = supply.withWater(supply.water() - 1);
			return true;
		}

		private boolean useWaterBottle() {
			Inventory inventory = player.getInventory();
			if (!consumeOne(inventory, InkItems::isWaterBottle)) {
				return false;
			}
			ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
			if (!inventory.add(bottle)) {
				player.drop(bottle, false);
			}
			return true;
		}

		void save() {
			if (!free) {
				player.setAttached(InkSupply.ATTACHMENT, supply);
			}
		}
	}
}
