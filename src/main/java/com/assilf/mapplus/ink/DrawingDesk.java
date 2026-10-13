package com.assilf.mapplus.ink;

import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.atlas.Atlas;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;

/**
 * A cartography table used by a player who has the mod: besides vanilla's
 * recipes, it draws on maps. Implemented on {@link CartographyTableMenu}.
 */
public interface DrawingDesk {
	/** Whether this table draws: the player using it has the mod. */
	boolean mapplus$isDesk();

	void mapplus$setDesk(boolean desk);

	/** The map holding the drawing on the empty map in the first slot, or null before anything is drawn. */
	MapId mapplus$canvas();

	void mapplus$setCanvas(MapId canvas);

	/** Whether a loom stands next to the table. Only known on the server. */
	boolean mapplus$hasLoom();

	static DrawingDesk of(CartographyTableMenu menu) {
		return (DrawingDesk) menu;
	}

	/** Whether labels, pins and borders can be used at this table: a loom stands next to it. */
	static boolean mayMark(CartographyTableMenu menu) {
		return !MapPlusConfig.get().markingNeedsLoom || of(menu).mapplus$hasLoom();
	}

	/** Whether the tool on the cursor may be used at this table. */
	static boolean mayUse(CartographyTableMenu menu, InkTool tool) {
		return tool != null && (!tool.marking() || mayMark(menu));
	}

	/** Whether an item is a blank canvas in the first slot: an empty map, or a sheet of paper. */
	static boolean isBlankCanvas(ItemStack stack) {
		return stack.is(Items.MAP) || stack.is(Items.PAPER);
	}

	/** The item for a blank canvas drawn on: a map of nowhere, so it's called a drawing. */
	static ItemStack canvasItem(MapId canvas) {
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.set(DataComponents.MAP_ID, canvas);
		stack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("mapplus.drawing", "Drawing"));
		return stack;
	}

	/** The map shown on a table's canvas: the map in the first slot, or the drawing on a blank canvas. */
	static MapId shownMap(CartographyTableMenu menu) {
		ItemStack input = menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem();
		if (input.is(Items.FILLED_MAP)) {
			return input.get(DataComponents.MAP_ID);
		}
		if (isBlankCanvas(input)) {
			return of(menu).mapplus$canvas();
		}
		return null;
	}

	/**
	 * Whether a player is looking at a map without carrying it: on a drawing
	 * desk, or in an atlas on a lectern. Such players keep receiving the map's
	 * updates like players who carry it.
	 */
	static boolean isViewing(Player player, MapId mapId) {
		if (mapId == null) {
			return false;
		}
		if (player.containerMenu instanceof CartographyTableMenu menu) {
			return of(menu).mapplus$isDesk() && mapId.equals(shownMap(menu));
		}
		if (player.containerMenu instanceof LecternMenu lectern) {
			return Atlas.read(lectern.getBook()).map(atlas -> atlas.contains(mapId)).orElse(false);
		}
		return false;
	}
}
