package com.assilf.mapplus.ink;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.net.InkStrokePayload;
import com.assilf.mapplus.net.MarkEditPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
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
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.DecoratedPotPattern;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Server side of marks: placed, changed, moved, resized and removed at a
 * drawing desk that has a loom next to it.
 * <ul>
 * <li>a label is written with a sign on the cursor, in the ink of the second
 * slot, and costs ink for its letters</li>
 * <li>a pin is a banner on the cursor: its colors and patterns, and its anvil
 * name if it has one</li>
 * <li>an icon is a pottery sherd's picture, stamped in the ink of the second
 * slot, with the sherd's anvil name if it has one</li>
 * <li>moving, resizing or removing a mark takes the same tool</li>
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
		if (!desk.mapplus$isDesk() || !DrawingDesk.mayMark(menu) || tool == null || tool.mark() == null) {
			return false;
		}

		// The edit must be meant for the map on the table now.
		ItemStack input = menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem();
		MapId mapId;
		if (input.is(Items.FILLED_MAP)) {
			mapId = input.get(DataComponents.MAP_ID);
		} else if (DrawingDesk.isBlankCanvas(input)) {
			mapId = desk.mapplus$canvas();
		} else {
			return false;
		}
		boolean newCanvas = mapId == null;
		if (newCanvas ? payload.mapId() != InkStrokePayload.NEW_CANVAS : payload.mapId() != mapId.id()) {
			return false;
		}
		MarkEditPayload.Action action = payload.action();
		// Only ink pays for a blank canvas to be started: a label or an icon.
		if (newCanvas && action != MarkEditPayload.Action.WRITE && action != MarkEditPayload.Action.ICON) {
			return false;
		}

		InkData data = InkData.get(level.getServer());
		InkLayer layer = newCanvas ? null : data.find(mapId);
		if (layer != null && layer.isSealed()) {
			return false;
		}
		MapPlusConfig config = MapPlusConfig.get();
		Slot materialSlot = menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT);
		InkMaterial material = InkMaterial.of(materialSlot.getItem());
		DyeColor ink = material != null && material.kind() == InkMaterial.Kind.DYE ? material.dye() : null;
		int size = Mark.clampSize(payload.size());

		Mark changed;
		switch (action) {
			case WRITE, REWRITE -> {
				if (tool != InkTool.LABEL || ink == null) {
					return false;
				}
				String text = Mark.clean(StringUtil.filterText(payload.text()));
				int cost = Mark.cost(text, size);
				if (cost == 0) {
					return false;
				}
				Mark old = null;
				if (action == MarkEditPayload.Action.REWRITE) {
					old = layer == null ? null : layer.findMark(payload.markId());
					if (old == null || !old.isLabel()) {
						return false;
					}
					// Rewriting in the same ink pays only for what it adds.
					if (old.color() == ink) {
						cost = Math.max(0, cost - old.cost());
					}
				} else if (count(layer, Mark.Kind.LABEL) >= config.maxLabelsPerMap) {
					return false;
				}
				if (!pay(player, material, materialSlot, cost)) {
					return false;
				}
				if (newCanvas) {
					mapId = startCanvas(level, player, desk);
				}
				if (layer == null) {
					layer = data.getOrCreate(mapId);
				}
				int id = old != null ? old.id() : layer.nextMarkId();
				int x = old != null ? old.x() : payload.x();
				int y = old != null ? old.y() : payload.y();
				changed = new Mark(id, Mark.Kind.LABEL, x, y, ink, text, size, Optional.of(player.getUUID()));
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
				if (count(layer, Mark.Kind.PIN) + count(layer, Mark.Kind.ICON) >= config.maxPinsPerMap) {
					return false;
				}
				List<Mark.Layer> layers = new ArrayList<>();
				BannerPatternLayers patterns = banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
				for (BannerPatternLayers.Layer pattern : patterns.layers()) {
					layers.add(new Mark.Layer(pattern.pattern().value().assetId().toString(), pattern.color()));
				}
				changed = new Mark(layer.nextMarkId(), Mark.Kind.PIN, payload.x(), payload.y(), bannerItem.getColor(), name(banner), size,
						Optional.of(player.getUUID()), Ink.FULL, "", layers);
				layer.putMark(changed);
				level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			case ICON -> {
				ItemStack sherd = menu.getCarried();
				Holder<DecoratedPotPattern> pattern = sherd.get(DataComponents.PROVIDES_POTTERY_PATTERN);
				if (tool != InkTool.ICON || pattern == null || ink == null) {
					return false;
				}
				if (count(layer, Mark.Kind.PIN) + count(layer, Mark.Kind.ICON) >= config.maxPinsPerMap) {
					return false;
				}
				if (!pay(player, material, materialSlot, Mark.iconCost(size))) {
					return false;
				}
				if (newCanvas) {
					mapId = startCanvas(level, player, desk);
				}
				if (layer == null) {
					layer = data.getOrCreate(mapId);
				}
				changed = new Mark(layer.nextMarkId(), Mark.Kind.ICON, payload.x(), payload.y(), ink, name(sherd), size,
						Optional.of(player.getUUID()), Ink.FULL, pattern.value().assetId().toString(), List.of());
				layer.putMark(changed);
				level.playSound(null, player.blockPosition(), SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
			}
			case MOVE, RESIZE, REMOVE -> {
				Mark mark = layer == null ? null : layer.findMark(payload.markId());
				// A label moves with a sign, a pin with a banner, an icon with a sherd.
				if (mark == null || tool != InkTool.of(mark.kind())) {
					return false;
				}
				if (action == MarkEditPayload.Action.MOVE) {
					changed = mark.at(payload.x(), payload.y());
					layer.putMark(changed);
				} else if (action == MarkEditPayload.Action.RESIZE) {
					changed = mark.withSize(size);
					if (size == mark.size()) {
						return false;
					}
					// Growing a label or icon takes more of its own ink; shrinking it is free.
					int cost = Math.max(0, changed.cost() - mark.cost());
					if (cost > 0 && (ink != mark.color() || !pay(player, material, materialSlot, cost))) {
						return false;
					}
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
		if (config.logMarks && action != MarkEditPayload.Action.MOVE && action != MarkEditPayload.Action.RESIZE) {
			MapPlus.LOGGER.info("{} {} {} \"{}\" on map #{}", player.getName().getString(),
					action == MarkEditPayload.Action.REMOVE ? "removed" : "placed",
					changed.kind().name().toLowerCase(Locale.ROOT), changed.text(), mapId.id());
		}
		return true;
	}

	private static int count(InkLayer layer, Mark.Kind kind) {
		return layer == null ? 0 : layer.countMarks(kind);
	}

	/** An item's anvil name, cleaned like a label, or empty for none. */
	private static String name(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : Mark.clean(StringUtil.filterText(name.getString()));
	}

	/** Pays for a mark with the ink in the second slot, all or nothing. */
	private static boolean pay(Player player, InkMaterial material, Slot source, int cost) {
		if (cost <= 0) {
			return true;
		}
		InkManager.Wallet wallet = new InkManager.Wallet(player, material, source);
		if (!wallet.canAfford(cost)) {
			return false;
		}
		for (int i = 0; i < cost; i++) {
			wallet.spend();
		}
		wallet.save();
		return true;
	}

	private static MapId startCanvas(ServerLevel level, Player player, DrawingDesk desk) {
		MapId mapId = InkManager.createCanvas(level, player);
		desk.mapplus$setCanvas(mapId);
		return mapId;
	}
}
