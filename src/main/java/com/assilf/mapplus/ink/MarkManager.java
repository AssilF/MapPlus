package com.assilf.mapplus.ink;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.net.InkStrokePayload;
import com.assilf.mapplus.net.MarkEditPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;

/**
 * Server side of labels and pins: placed, changed, moved and removed at a
 * drawing desk that has a loom next to it.
 * <ul>
 * <li>a label is written with a sign on the cursor, in the ink of the second
 * slot, and costs ink for its letters</li>
 * <li>a pin is a banner on the cursor: its color, and its anvil name if it has one</li>
 * <li>moving or removing a mark takes the same tool</li>
 * </ul>
 */
public final class MarkManager {
	private MarkManager() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(MarkEditPayload.TYPE, (payload, context) -> {
			if (InkManager.acknowledge(context.player(), payload.seq()) && InkManager.allowMark(context.player())
					&& context.player().containerMenu instanceof CartographyTableMenu menu) {
				edit(context.player(), menu, context.player().level(), payload);
			}
		});
	}

	/** Applies a mark edit, if the table, the tools and the ink allow it. */
	public static boolean edit(Player player, CartographyTableMenu menu, ServerLevel level, MarkEditPayload payload) {
		DrawingDesk desk = DrawingDesk.of(menu);
		InkTool tool = InkTool.of(menu.getCarried());
		if (!desk.mapplus$isDesk() || !DrawingDesk.mayMark(menu) || (tool != InkTool.LABEL && tool != InkTool.PIN)) {
			return false;
		}

		// The edit must be meant for the map on the table now.
		ItemStack input = menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem();
		MapId mapId;
		if (input.is(Items.FILLED_MAP)) {
			mapId = input.get(DataComponents.MAP_ID);
		} else if (input.is(Items.MAP)) {
			mapId = desk.mapplus$canvas();
		} else {
			return false;
		}
		boolean newCanvas = mapId == null;
		if (newCanvas ? payload.mapId() != InkStrokePayload.NEW_CANVAS : payload.mapId() != mapId.id()) {
			return false;
		}
		// Only a label pays for a blank canvas to be started.
		if (newCanvas && payload.action() != MarkEditPayload.Action.WRITE) {
			return false;
		}

		InkData data = InkData.get(level.getServer());
		InkLayer layer = newCanvas ? null : data.find(mapId);
		if (layer != null && layer.isSealed()) {
			return false;
		}
		MapPlusConfig config = MapPlusConfig.get();

		Mark changed;
		switch (payload.action()) {
			case WRITE, REWRITE -> {
				if (tool != InkTool.LABEL) {
					return false;
				}
				Slot materialSlot = menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT);
				InkMaterial material = InkMaterial.of(materialSlot.getItem());
				if (material == null || material.kind() != InkMaterial.Kind.DYE) {
					return false;
				}
				String text = Mark.clean(StringUtil.filterText(payload.text()));
				int size = Mark.clampSize(payload.size());
				int cost = Mark.cost(text, size);
				if (cost == 0) {
					return false;
				}
				Mark old = null;
				if (payload.action() == MarkEditPayload.Action.REWRITE) {
					old = layer == null ? null : layer.findMark(payload.markId());
					if (old == null || !old.isLabel()) {
						return false;
					}
					// Rewriting in the same ink pays only for what it adds.
					if (old.color() == material.dye()) {
						cost = Math.max(0, cost - Mark.cost(old.text(), old.size()));
					}
				} else if ((layer == null ? 0 : layer.countMarks(Mark.Kind.LABEL)) >= config.maxLabelsPerMap) {
					return false;
				}
				InkManager.Wallet wallet = new InkManager.Wallet(player, material, materialSlot);
				if (!wallet.canAfford(cost)) {
					return false;
				}
				for (int i = 0; i < cost; i++) {
					wallet.spend();
				}
				wallet.save();
				if (newCanvas) {
					mapId = InkManager.createCanvas(level, player);
					desk.mapplus$setCanvas(mapId);
				}
				if (layer == null) {
					layer = data.getOrCreate(mapId);
				}
				int id = old != null ? old.id() : layer.nextMarkId();
				int x = old != null ? old.x() : payload.x();
				int y = old != null ? old.y() : payload.y();
				changed = new Mark(id, Mark.Kind.LABEL, x, y, material.dye(), text, size, Optional.of(player.getUUID()));
				layer.putMark(changed);
				level.playSound(null, player.blockPosition(), SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			case PIN -> {
				ItemStack banner = menu.getCarried();
				if (tool != InkTool.PIN || !(banner.getItem() instanceof BannerItem bannerItem)) {
					return false;
				}
				if (layer == null) {
					layer = data.getOrCreate(mapId);
				}
				if (layer.countMarks(Mark.Kind.PIN) >= config.maxPinsPerMap) {
					return false;
				}
				Component name = banner.get(DataComponents.CUSTOM_NAME);
				String text = name == null ? "" : Mark.clean(StringUtil.filterText(name.getString()));
				DyeColor color = bannerItem.getColor();
				changed = new Mark(layer.nextMarkId(), Mark.Kind.PIN, payload.x(), payload.y(), color, text, Mark.MEDIUM,
						Optional.of(player.getUUID()));
				layer.putMark(changed);
				level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			case MOVE, REMOVE -> {
				Mark mark = layer == null ? null : layer.findMark(payload.markId());
				// A label moves with a sign, a pin with a banner.
				if (mark == null || (mark.isLabel() ? tool != InkTool.LABEL : tool != InkTool.PIN)) {
					return false;
				}
				if (payload.action() == MarkEditPayload.Action.MOVE) {
					changed = mark.at(payload.x(), payload.y());
					layer.putMark(changed);
				} else {
					changed = mark;
					layer.removeMark(mark.id());
					level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
				}
			}
			default -> {
				return false;
			}
		}

		data.setDirty();
		MapItemSavedData map = level.getMapData(mapId);
		if (map != null) {
			InkManager.marksChanged(map);
		}
		if (config.logMarks && payload.action() != MarkEditPayload.Action.MOVE) {
			MapPlus.LOGGER.info("{} {} {} \"{}\" on map #{}", player.getName().getString(),
					payload.action() == MarkEditPayload.Action.REMOVE ? "removed" : "placed",
					changed.isLabel() ? "label" : "pin", changed.text(), mapId.id());
		}
		return true;
	}
}
