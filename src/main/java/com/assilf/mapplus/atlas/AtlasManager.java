package com.assilf.mapplus.atlas;

import com.assilf.mapplus.net.AtlasActionPayload;
import com.assilf.mapplus.net.InkStatePayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Server side of atlases: keeps their maps in sync, lets the selected map
 * explore while the atlas is held, and applies page changes.
 */
public final class AtlasManager {
	private AtlasManager() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(AtlasActionPayload.TYPE, (payload, context) ->
				handleAction(context.player(), payload));

		// Players with the mod open the atlas screen on their side; skip the vanilla
		// book screen for them. Players without the mod get the plain book.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			if (Atlas.isAtlas(player.getItemInHand(hand)) && ServerPlayNetworking.canSend(serverPlayer, InkStatePayload.TYPE)) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		ServerTickEvents.END_SERVER_TICK.register(AtlasManager::tick);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (Atlas.isBook(stack)) {
					Atlas.read(stack).ifPresent(atlas -> syncMaps(player, atlas, false));
				}
			}
			for (InteractionHand hand : InteractionHand.values()) {
				Atlas.read(player.getItemInHand(hand)).ifPresent(atlas -> syncMaps(player, atlas, true));
			}
		}
	}

	/**
	 * Treats the atlas's maps like maps carried in the inventory: the player shows
	 * on them and receives their updates. A held atlas also explores with its
	 * selected map, like a held map.
	 */
	private static void syncMaps(ServerPlayer player, Atlas atlas, boolean held) {
		ServerLevel level = player.level();
		for (int page = 0; page < atlas.plates().size(); page++) {
			Atlas.Plate plate = atlas.plates().get(page);
			MapId mapId = plate.id();
			MapItemSavedData map = level.getMapData(mapId);
			if (map == null) {
				continue;
			}
			if (held) {
				if (page == atlas.selected() && !plate.sketch() && !map.locked) {
					((MapItem) Items.FILLED_MAP).update(level, player, map);
				}
				continue;
			}
			// The bound item carries explorer and treasure markers, which vanilla
			// re-applies from the carried stack.
			map.tickCarriedBy(player, plate.mapStack(level.registryAccess()), null);
			Packet<?> packet = map.getUpdatePacket(mapId, player);
			if (packet != null) {
				player.connection.send(packet);
			}
		}
	}

	private static void handleAction(ServerPlayer player, AtlasActionPayload payload) {
		InteractionHand hand = payload.hand();
		ItemStack stack = player.getItemInHand(hand);
		Optional<Atlas> read = Atlas.read(stack);
		if (read.isEmpty()) {
			return;
		}
		Atlas atlas = read.get();
		int page = payload.page();
		boolean pageExists = page >= 0 && page < atlas.plates().size();
		// Ignore actions aimed at a page that changed since the client saw it.
		boolean validPage = pageExists && atlas.plates().get(page).mapId() == payload.mapId();

		switch (payload.action()) {
			case SELECT -> {
				if (validPage && page != atlas.selected()) {
					atlas.withSelected(page).write(stack);
					playSound(player, SoundEvents.BOOK_PAGE_TURN);
				}
			}
			case ADD_SKETCH -> {
				if (!Atlas.isEditable(stack) || atlas.plates().size() >= Atlas.MAX_PLATES) {
					return;
				}
				if (!player.isCreative() && !consumeOne(player.getInventory(), s -> s.is(Items.PAPER))) {
					return;
				}
				MapId sketch = createSketch(player.level());
				atlas.withPlate(new Atlas.Plate(sketch.id(), true)).write(stack);
				playSound(player, SoundEvents.BOOK_PAGE_TURN);
			}
			case TAKE_OUT -> {
				if (!Atlas.isEditable(stack) || !validPage) {
					return;
				}
				Atlas.Plate plate = atlas.plates().get(page);
				atlas.withoutPlate(page).write(stack);
				WritableBookContent notes = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
				if (notes != null) {
					stack.set(DataComponents.WRITABLE_BOOK_CONTENT, Atlas.withNoteMovedToEnd(notes, page));
				}
				ItemStack map = plate.mapStack(player.level().registryAccess()).copy();
				if (!player.getInventory().add(map)) {
					Block.popResource(player.level(), player.blockPosition(), map);
				}
				playSound(player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT);
			}
			case BOOKMARK -> {
				if (!Atlas.isEditable(stack) || !validPage) {
					return;
				}
				if (payload.arg() < 0) {
					atlas.withBookmark(page, null).write(stack);
					return;
				}
				if (payload.arg() >= DyeColor.values().length || atlas.bookmarks().stream().anyMatch(mark -> mark.page() == page)) {
					return;
				}
				DyeColor color = DyeColor.byId(payload.arg());
				if (!player.isCreative() && !consumeOne(player.getInventory(), s -> s.is(Items.DYE.pick(color)))) {
					player.sendOverlayMessage(Component.translatable("mapplus.atlas.need_dye"));
					return;
				}
				atlas.withBookmark(page, color).write(stack);
				playSound(player, SoundEvents.BOOK_PAGE_TURN);
			}
		}
	}

	/** A blank page to draw on: a locked map that never fills in. */
	private static MapId createSketch(ServerLevel level) {
		MapId id = level.getFreeMapId();
		MapItemSavedData blank = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, level.dimension()).locked();
		level.setMapData(id, blank);
		return id;
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

	private static void playSound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound) {
		player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.8F, 1.0F);
	}
}
