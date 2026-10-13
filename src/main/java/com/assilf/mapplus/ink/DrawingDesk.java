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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.util.Arrays;

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

	/** The sheet's size: a map on the table and the maps around it, three by three. */
	int SHEET = 3;

	/**
	 * The sheet a table's map lies in: the maps the player carries that border it,
	 * of the same scale and in the same dimension, as they'd lie around it on the
	 * table. Map ids row by row, north to south and west to east, with the table's
	 * map in the middle; -1 where the player carries none. Empty without a map on
	 * the table, and for drawings, which aren't anywhere.
	 */
	static int[] sheet(ServerLevel level, Player player, CartographyTableMenu menu) {
		int[] sheet = new int[SHEET * SHEET];
		Arrays.fill(sheet, -1);
		ItemStack input = menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem();
		MapId center = input.is(Items.FILLED_MAP) ? input.get(DataComponents.MAP_ID) : null;
		MapItemSavedData home = center == null ? null : level.getMapData(center);
		if (home == null || isDrawing(home)) {
			return sheet;
		}
		sheet[SHEET * SHEET / 2] = center.id();
		int span = Ink.SIZE << home.scale;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			MapId mapId = stack.is(Items.FILLED_MAP) ? stack.get(DataComponents.MAP_ID) : null;
			if (mapId == null || mapId.equals(center)) {
				continue;
			}
			MapItemSavedData map = level.getMapData(mapId);
			if (map == null || map.scale != home.scale || !map.dimension.equals(home.dimension) || isDrawing(map)) {
				continue;
			}
			int dx = map.centerX - home.centerX;
			int dz = map.centerZ - home.centerZ;
			if (dx % span != 0 || dz % span != 0 || Math.abs(dx / span) > 1 || Math.abs(dz / span) > 1) {
				continue;
			}
			int index = (dz / span + 1) * SHEET + dx / span + 1;
			if (sheet[index] < 0) {
				sheet[index] = mapId.id();
			}
		}
		return sheet;
	}

	/** Whether a map is on a table's sheet: the table's own map, or one the player carries beside it. */
	static boolean inSheet(ServerLevel level, Player player, CartographyTableMenu menu, int mapId) {
		for (int id : sheet(level, player, menu)) {
			if (id >= 0 && id == mapId) {
				return true;
			}
		}
		return false;
	}

	/** A drawing's map: locked, with nothing explored, so it isn't anywhere in the world. */
	static boolean isDrawing(MapItemSavedData map) {
		if (!map.locked) {
			return false;
		}
		for (byte color : map.colors) {
			if (color != 0) {
				return false;
			}
		}
		return true;
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
