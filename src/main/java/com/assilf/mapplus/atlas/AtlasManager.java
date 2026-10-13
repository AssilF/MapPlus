package com.assilf.mapplus.atlas;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.ink.MapViewers;
import com.assilf.mapplus.net.AtlasPagePayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;

/**
 * Server side of atlases: keeps their maps in sync for whoever carries them or
 * reads them on a lectern, and lets a held atlas explore with the map it was
 * last left open on.
 */
public final class AtlasManager {
	private AtlasManager() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(AtlasPagePayload.TYPE, (payload, context) ->
				leftOpenAt(context.player(), payload.hand(), payload.page()));
		ServerTickEvents.END_SERVER_TICK.register(AtlasManager::tick);
	}

	/**
	 * The player closed the atlas on a page. If a map is in that spread, it
	 * becomes the one that explores; otherwise the atlas stops exploring.
	 */
	public static void leftOpenAt(ServerPlayer player, InteractionHand hand, int page) {
		ItemStack stack = player.getItemInHand(hand);
		Optional<Atlas> read = Atlas.read(stack);
		if (read.isEmpty()) {
			return;
		}
		Atlas atlas = read.get();
		Atlas.Plate plate = page >= 0 ? atlas.mapInSpread(page) : null;
		int selected = plate == null ? -1 : plate.page();
		if (selected != atlas.selected()) {
			atlas.withSelected(selected).write(stack);
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (Atlas.isBook(stack)) {
					Atlas.read(stack).ifPresent(atlas -> carry(player, atlas));
				}
			}
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack held = player.getItemInHand(hand);
				if (Atlas.isOriginal(held)) {
					Atlas.read(held).ifPresent(atlas -> explore(player, atlas));
				}
			}
			if (player.containerMenu instanceof LecternMenu lectern && MapPlus.hasMod(player)) {
				Atlas.read(lectern.getBook()).ifPresent(atlas -> view(player, atlas));
			}
		}
	}

	/**
	 * Treats the atlas's maps like maps carried in the inventory: the player shows
	 * on them and, with the mod, receives their updates to see them in the book.
	 */
	private static void carry(ServerPlayer player, Atlas atlas) {
		ServerLevel level = player.level();
		boolean shown = MapPlus.hasMod(player);
		for (Atlas.Plate plate : atlas.plates()) {
			MapItemSavedData map = level.getMapData(plate.id());
			if (map == null) {
				continue;
			}
			// The stitched item carries explorer and treasure markers, which vanilla
			// re-applies from the carried stack.
			map.tickCarriedBy(player, plate.mapStack(level.registryAccess()), null);
			if (shown) {
				Packet<?> packet = map.getUpdatePacket(plate.id(), player);
				if (packet != null) {
					player.connection.send(packet);
				}
			}
		}
	}

	/**
	 * A held atlas explores with its selected map, like a held map. Only the
	 * original does: copies show the maps but don't fill them in.
	 */
	private static void explore(ServerPlayer player, Atlas atlas) {
		atlas.selectedPlate().ifPresent(plate -> {
			MapItemSavedData map = player.level().getMapData(plate.id());
			if (map != null && !map.locked) {
				((MapItem) Items.FILLED_MAP).update(player.level(), player, map);
			}
		});
	}

	/** An atlas on a lectern: the reader receives its maps' updates without showing on them. */
	private static void view(ServerPlayer player, Atlas atlas) {
		for (Atlas.Plate plate : atlas.plates()) {
			MapItemSavedData map = player.level().getMapData(plate.id());
			if (map != null) {
				MapViewers.view(player, plate.id(), map);
			}
		}
	}
}
