package com.assilf.mapplus.client.desk;

import com.assilf.mapplus.client.marks.ClientMarks;
import com.assilf.mapplus.client.marks.Labels;
import com.assilf.mapplus.client.marks.MarkRenderer;
import com.assilf.mapplus.client.mixin.AbstractContainerScreenAccessor;
import com.assilf.mapplus.ink.DrawingDesk;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.InkItems;
import com.assilf.mapplus.ink.InkManager;
import com.assilf.mapplus.ink.InkMaterial;
import com.assilf.mapplus.ink.InkTool;
import com.assilf.mapplus.ink.Mark;
import com.assilf.mapplus.net.InkStatePayload;
import com.assilf.mapplus.net.InkStrokePayload;
import com.assilf.mapplus.net.MarkEditPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/**
 * The drawing desk: a wing on the right of the vanilla cartography table
 * window, in the same style, holding the map at full size. What the player
 * carries on the cursor over the map is the tool; the rack under the map picks
 * tools up from the inventory. Shown to players who play with the mod on both
 * sides.
 *
 * <p>Zoomed out, the desk is a drafting table: the maps the player carries that
 * border the one on the table lie around it, a sheet of three by three at a
 * third of their size, and the pen, brush, string, frame and chain draw
 * straight across them. Clicking a map there with an empty hand puts it on the
 * table instead.
 */
public final class DeskWing {
	/** The vanilla window is this wide; the wing makes it {@link #WIDTH}. */
	public static final int VANILLA_WIDTH = 176;
	public static final int WIDTH = 338;
	private static final int HEIGHT = 166;

	private static final int CANVAS_X = 187;
	private static final int CANVAS_Y = 8;
	private static final int RACK_X = 179;
	private static final int RACK_Y = 141;
	/** The inks the player has, in a column beside the map: click one to put it in the second slot. */
	private static final int PALETTE_X = 318;
	private static final int SWATCH = 7;
	private static final int BUTTON_WIDTH = 16;
	private static final int BUTTON_HEIGHT = 18;
	/** Borders are dashes this long, with gaps this long. */
	private static final int DASH = 3;
	private static final int GAP = 2;
	/** How long the ink's strength is shown after scrolling, in ticks. */
	private static final int STRENGTH_SHOWN_TICKS = 30;
	/** How long an edit is shown before the server's answer, at most. */
	private static final int PENDING_TICKS = 60;
	/** The vanilla button that swaps a slot with the off hand. */
	private static final int OFF_HAND_BUTTON = 40;
	/**
	 * A left click, as container clicks count buttons: 0, whatever number the
	 * mouse's left button has, so a click takes or puts down a whole stack.
	 */
	private static final int LEFT_CLICK = 0;
	/** The sheet, zoomed out: the map on the table in the middle, the maps carried around it, at a third of their size. */
	private static final int SHEET = DrawingDesk.SHEET;
	private static final int SHEET_WIDTH = Ink.SIZE * SHEET;
	private static final int MIDDLE = SHEET * SHEET / 2;
	/** The button beside the rack that lays the sheet out. */
	private static final int ZOOM_X = 323;
	private static final int ZOOM_WIDTH = 11;

	private static final int FILL = 0xFFC6C6C6;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_INSIDE = 0xFF8B8B8B;
	private static final int FADED = 0xB08B8B8B;

	private static final Identifier TABLE_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/cartography_table.png");
	private static final Identifier MAP_BACKGROUND = Identifier.withDefaultNamespace("textures/map/map_background.png");
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("container/stonecutter/recipe");
	private static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");
	private static final Identifier BUTTON_SELECTED = Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");

	private static final InkTool[] RACK = InkTool.values();

	/** One ink on the palette: what item it is, how it looks there, and what it's called. */
	private record Swatch(java.util.function.Predicate<ItemStack> item, int color, Component name) {
	}

	private static final List<Swatch> PALETTE = palette();

	private static List<Swatch> palette() {
		List<Swatch> swatches = new ArrayList<>();
		for (DyeColor dye : DyeColor.values()) {
			swatches.add(new Swatch(stack -> stack.is(InkItems.dyeItem(dye)) || dye == DyeColor.BLACK && stack.is(Items.INK_SAC),
					dye.getMapColor().calculateARGBColor(net.minecraft.world.level.material.MapColor.Brightness.NORMAL),
					Component.translatable("color.minecraft." + dye.getName())));
		}
		swatches.add(new Swatch(stack -> stack.is(Items.GLOW_INK_SAC), 0xFF7FF5D0, Items.GLOW_INK_SAC.getName(new ItemStack(Items.GLOW_INK_SAC))));
		swatches.add(new Swatch(InkItems::isWaterBottle, 0xFF3F76E4, Component.translatable("mapplus.desk.palette.water")));
		return List.copyOf(swatches);
	}

	private final CartographyTableScreen screen;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final MapRenderState mapRenderState = new MapRenderState();
	private final InkPreview preview = new InkPreview();
	private int ticks;

	/** The tool of the stroke being drawn, or null, and whether it's erasing (drawn with the right button). */
	private InkTool stroke;
	private boolean erasing;
	/** Where the last pen stroke ended, for Shift-clicking a straight line on from it. */
	private int penEndX = -1;
	private int penEndY = -1;
	/** Ticks until the pen's scratch is heard again. */
	private int scratchTicks;
	private int anchorX;
	private int anchorY;
	private int lastX;
	private int lastY;
	/** Pen pixels not sent yet, and the stroke number they'll be sent under. */
	private final BitSet penPixels = new BitSet(Ink.PIXELS);
	private int penSeq;
	/** The line, box or border being dragged out. */
	private final BitSet shape = new BitSet(Ink.PIXELS);

	/** The mouse button pressed on the desk; its drag and release belong to the desk too. */
	private int pressButton = -1;

	/** The label or pin being dragged, where it was grabbed, where to, and whether it has moved yet. */
	private Mark dragged;
	private int pressX;
	private int pressY;
	private int dragX;
	private int dragY;
	private boolean dragMoved;

	/** The label being typed, or null. */
	private LabelEdit editing;
	private double scrolled;

	/**
	 * How watered down the ink is drawn, {@link Ink#FULL} to {@link Ink#FAINTEST}:
	 * scrolled over the map with a drawing tool. Kept from table to table.
	 */
	private static int strength = Ink.FULL;
	private int strengthShown;
	/** Every pixel the pen has touched this stroke, so a pixel crossed twice is inked once. */
	private final BitSet penStroke = new BitSet(Ink.PIXELS);

	/** Whether the desk shows the sheet of maps rather than the one on the table. Kept from table to table. */
	private static boolean zoomedOut;
	private final MapRenderState[] sheetStates = new MapRenderState[SHEET * SHEET];
	private final InkPreview sheetPreview = new InkPreview(SHEET_WIDTH);
	/** Whether the stroke being drawn is on the sheet, crossing maps; its pen pixels, all it has touched, and its shape. */
	private boolean onSheet;
	private final BitSet sheetPen = new BitSet(SHEET_WIDTH * SHEET_WIDTH);
	private final BitSet sheetPenStroke = new BitSet(SHEET_WIDTH * SHEET_WIDTH);
	private final BitSet sheetShape = new BitSet(SHEET_WIDTH * SHEET_WIDTH);

	/** A mark edit sent to the server, shown until the map's marks come back changed. */
	private Mark pending;
	private int pendingHidden = -1;
	private int pendingSeq;
	private int pendingMap;
	private int pendingUpdates;
	private int pendingTicks;

	/** A label being written or rewritten: where, how big, and what's typed so far. */
	private static final class LabelEdit {
		final int markId;
		final Mark old;
		int x;
		int y;
		int size;
		final StringBuilder text;

		LabelEdit(Mark old, int x, int y, int size, String text) {
			this.old = old;
			this.markId = old == null ? -1 : old.id();
			this.x = x;
			this.y = y;
			this.size = size;
			this.text = new StringBuilder(text);
		}
	}

	public DeskWing(CartographyTableScreen screen) {
		this.screen = screen;
		for (int i = 0; i < sheetStates.length; i++) {
			sheetStates[i] = new MapRenderState();
		}
	}

	private int left() {
		return ((AbstractContainerScreenAccessor) screen).mapplus$leftPos();
	}

	private int top() {
		return ((AbstractContainerScreenAccessor) screen).mapplus$topPos();
	}

	private CartographyTableMenu menu() {
		return screen.getMenu();
	}

	// What's on the table

	private ItemStack input() {
		return menu().getSlot(CartographyTableMenu.MAP_SLOT).getItem();
	}

	private InkMaterial material() {
		return InkMaterial.of(menu().getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem());
	}

	/** The dye a label would be written in, or null if the second slot holds no dye or ink sac. */
	private DyeColor labelInk() {
		InkMaterial material = material();
		return material != null && material.kind() == InkMaterial.Kind.DYE ? material.dye() : null;
	}

	/** The tool on the cursor, or null. */
	private InkTool tool() {
		return InkTool.of(menu().getCarried());
	}

	/** Whether a tool can be used at this table: marking tools need a loom next to it. */
	private boolean unlocked(InkTool tool) {
		return tool != null && (!tool.marking() || DeskState.get().loom());
	}

	/**
	 * The map shown on the canvas, or null for a blank canvas or no map. On an
	 * empty map, the drawing is whatever the server put in the result slot.
	 */
	private MapId shownMap() {
		ItemStack input = input();
		if (input.is(Items.FILLED_MAP)) {
			return input.get(DataComponents.MAP_ID);
		}
		ItemStack result = menu().getSlot(CartographyTableMenu.RESULT_SLOT).getItem();
		return DrawingDesk.isBlankCanvas(input) && result.is(Items.FILLED_MAP) ? result.get(DataComponents.MAP_ID) : null;
	}

	private boolean hasCanvas() {
		ItemStack input = input();
		return input.is(Items.FILLED_MAP) || DrawingDesk.isBlankCanvas(input);
	}

	private boolean sealed() {
		return DeskState.get().sealed();
	}

	private boolean canDraw(InkTool tool) {
		return tool != null && tool.draws() && unlocked(tool) && hasCanvas() && material() != null && !sealed();
	}

	/** Erasing needs a map with ink to erase, and no ink in hand at all. */
	private boolean canErase(InkTool tool) {
		return tool != null && tool.draws() && unlocked(tool) && shownMap() != null && !sealed();
	}

	private boolean canWrite() {
		return unlocked(InkTool.LABEL) && hasCanvas() && labelInk() != null && !sealed();
	}

	/** Pins go on maps that exist: a filled map, or a blank canvas that has been drawn on. */
	private boolean canPin() {
		return unlocked(InkTool.PIN) && shownMap() != null && !sealed();
	}

	/** Icons are stamped in ink, so they can start a blank canvas as a label can. */
	private boolean canStamp() {
		return unlocked(InkTool.ICON) && hasCanvas() && labelInk() != null && !sealed();
	}

	private MapItemSavedData mapData(MapId mapId) {
		return mapId == null || minecraft.level == null ? null : minecraft.level.getMapData(mapId);
	}

	// The sheet

	/** Whether the map on the table can be laid out with the maps around it: a map of somewhere, not a drawing. */
	private boolean canLayOut() {
		MapId mapId = shownMap();
		return input().is(Items.FILLED_MAP) && mapId != null && DeskState.get().sheetMap(MIDDLE) == mapId.id();
	}

	private boolean sheetShown() {
		return zoomedOut && input().is(Items.FILLED_MAP);
	}

	/**
	 * The maps on the sheet, row by row. Until the server has laid out a map
	 * just put on the table, it lies alone in the middle.
	 */
	private int[] sheet() {
		int[] sheet = new int[SHEET * SHEET];
		java.util.Arrays.fill(sheet, -1);
		MapId mapId = shownMap();
		InkStatePayload state = DeskState.get();
		if (mapId != null && state.sheetMap(MIDDLE) == mapId.id()) {
			for (int i = 0; i < sheet.length; i++) {
				sheet[i] = state.sheetMap(i);
			}
		} else if (mapId != null) {
			sheet[MIDDLE] = mapId.id();
		}
		return sheet;
	}

	/** Whether the map at a place on the sheet is sealed against drawing. */
	private boolean sealedOnSheet(int index) {
		return (DeskState.get().sealedSheet() >> index & 1) != 0;
	}

	/** The sheet pixel under the mouse, a third of a screen pixel each way. */
	private int sheetX(double mouseX) {
		return (int) Math.floor((mouseX - left() - CANVAS_X) * SHEET);
	}

	private int sheetY(double mouseY) {
		return (int) Math.floor((mouseY - top() - CANVAS_Y) * SHEET);
	}

	/** The place on the sheet a sheet pixel is in. */
	private static int tile(int x, int y) {
		return Math.clamp(y / Ink.SIZE, 0, SHEET - 1) * SHEET + Math.clamp(x / Ink.SIZE, 0, SHEET - 1);
	}

	private void setZoomedOut(boolean out) {
		if (zoomedOut == out) {
			return;
		}
		flushPen();
		flushSheetPen();
		stroke = null;
		onSheet = false;
		shape.clear();
		sheetShape.clear();
		zoomedOut = out;
		// Maps spread over the table, or gathered back.
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, out ? 0.9F : 1.1F, 0.8F));
	}

	/**
	 * Puts a map the player carries on the table, and the table's map where it
	 * was, by swapping each through the off hand as the F key would, so the tool
	 * on the cursor stays there.
	 */
	private void putOnTable(int mapId) {
		Slot from = null;
		for (Slot slot : menu().slots) {
			if (slot.container instanceof Inventory && isMap(slot.getItem(), mapId)) {
				from = slot;
				break;
			}
		}
		if (from != null) {
			click(from.index, OFF_HAND_BUTTON, ContainerInput.SWAP);
			click(CartographyTableMenu.MAP_SLOT, OFF_HAND_BUTTON, ContainerInput.SWAP);
			click(from.index, OFF_HAND_BUTTON, ContainerInput.SWAP);
		} else if (minecraft.player != null && isMap(minecraft.player.getOffhandItem(), mapId)) {
			click(CartographyTableMenu.MAP_SLOT, OFF_HAND_BUTTON, ContainerInput.SWAP);
		} else {
			deny();
			return;
		}
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.8F));
	}

	private static boolean isMap(ItemStack stack, int mapId) {
		MapId id = stack.get(DataComponents.MAP_ID);
		return stack.is(Items.FILLED_MAP) && id != null && id.id() == mapId;
	}

	// Geometry

	private boolean over(double mouseX, double mouseY, int x, int y, int width, int height) {
		int left = left();
		int top = top();
		return mouseX >= left + x && mouseX < left + x + width && mouseY >= top + y && mouseY < top + y + height;
	}

	private boolean overWing(double mouseX, double mouseY) {
		return over(mouseX, mouseY, VANILLA_WIDTH, 0, WIDTH - VANILLA_WIDTH, HEIGHT);
	}

	private boolean overCanvas(double mouseX, double mouseY) {
		return over(mouseX, mouseY, CANVAS_X, CANVAS_Y, Ink.SIZE, Ink.SIZE);
	}

	/** The canvas pixel under the mouse; outside the canvas while dragging, so strokes are cut at its edge. */
	private int pixelX(double mouseX) {
		return (int) Math.floor(mouseX - left() - CANVAS_X);
	}

	private int pixelY(double mouseY) {
		return (int) Math.floor(mouseY - top() - CANVAS_Y);
	}

	private int rackX(int index) {
		return RACK_X + index * BUTTON_WIDTH;
	}

	/** Whether a tool is carried over the map, so it's drawn held by its tip. */
	public boolean holdsToolOverMap(double mouseX, double mouseY) {
		return tool() != null && overCanvas(mouseX, mouseY) && hasCanvas();
	}

	// Rendering

	/** Drawn after the vanilla window and before its slots. */
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int left = left();
		int top = top();
		int edge = VANILLA_WIDTH - 4;

		// The window's own edges, taken from the vanilla texture, carried on to the new right side.
		graphics.fill(left + edge, top + 4, left + WIDTH - 4, top + HEIGHT - 4, FILL);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TABLE_TEXTURE, left + edge, top, 8, 0, WIDTH - 4 - edge, 4, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TABLE_TEXTURE, left + edge, top + HEIGHT - 4, 8, HEIGHT - 4, WIDTH - 4 - edge, 4, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TABLE_TEXTURE, left + WIDTH - 4, top, VANILLA_WIDTH - 4, 0, 4, HEIGHT, 256, 256);

		extractCanvas(graphics, left + CANVAS_X, top + CANVAS_Y, mouseX, mouseY);

		InkTool carried = tool();
		for (int i = 0; i < RACK.length; i++) {
			InkTool tool = RACK[i];
			int x = left + rackX(i);
			int y = top + RACK_Y;
			boolean usable = unlocked(tool) && (tool == carried || find(tool) != null);
			boolean hovered = over(mouseX, mouseY, rackX(i), RACK_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
			Identifier sprite = tool == carried ? BUTTON_SELECTED : hovered && usable ? BUTTON_HIGHLIGHTED : BUTTON;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
			graphics.item(icon(tool), x, y + 1);
			if (!usable) {
				graphics.fill(x + 1, y + 1, x + BUTTON_WIDTH - 1, y + BUTTON_HEIGHT - 1, FADED);
			}
		}

		// The sheet button: a little sheet of three by three maps, the table's in the middle.
		int zoomX = left + ZOOM_X;
		int zoomY = top + RACK_Y;
		boolean canLayOut = canLayOut();
		boolean zoomHovered = over(mouseX, mouseY, ZOOM_X, RACK_Y, ZOOM_WIDTH, BUTTON_HEIGHT);
		Identifier zoomSprite = sheetShown() ? BUTTON_SELECTED : zoomHovered && canLayOut ? BUTTON_HIGHLIGHTED : BUTTON;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, zoomSprite, zoomX, zoomY, ZOOM_WIDTH, BUTTON_HEIGHT);
		for (int i = 0; i < SHEET * SHEET; i++) {
			int cellX = zoomX + 2 + i % SHEET * 3;
			int cellY = zoomY + 5 + i / SHEET * 3;
			graphics.fill(cellX, cellY, cellX + 2, cellY + 2, i == MIDDLE ? 0xFFF0E2C0 : 0xFF8F7A5A);
		}
		if (!canLayOut && !sheetShown()) {
			graphics.fill(zoomX + 1, zoomY + 1, zoomX + ZOOM_WIDTH - 1, zoomY + BUTTON_HEIGHT - 1, FADED);
		}

		// The palette: a swatch per ink, outlined when it's in the second slot, faded when the player has none.
		InkMaterial loaded = material();
		ItemStack inSlot = menu().getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem();
		for (int i = 0; i < PALETTE.size(); i++) {
			Swatch swatch = PALETTE.get(i);
			int x = left + PALETTE_X;
			int y = top + CANVAS_Y + i * SWATCH;
			boolean current = loaded != null && swatch.item().test(inSlot);
			graphics.fill(x, y, x + SWATCH - 1, y + SWATCH - 1, current ? SLOT_LIGHT : SLOT_DARK);
			graphics.fill(x + 1, y + 1, x + SWATCH - 2, y + SWATCH - 2, swatch.color());
			if (!current && count(swatch) == 0) {
				graphics.fill(x + 1, y + 1, x + SWATCH - 2, y + SWATCH - 2, FADED);
			}
		}
	}

	/** How many of an ink the player has in the inventory. */
	private int count(Swatch swatch) {
		int count = 0;
		for (Slot slot : menu().slots) {
			if (slot.container instanceof Inventory && swatch.item().test(slot.getItem())) {
				count += slot.getItem().getCount();
			}
		}
		return count;
	}

	/** Puts an ink from the inventory in the second slot, moving what's there back, as Shift-clicking each would. */
	private void loadInk(Swatch swatch) {
		ItemStack inSlot = menu().getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem();
		if (swatch.item().test(inSlot)) {
			return;
		}
		int from = -1;
		for (Slot slot : menu().slots) {
			if (slot.container instanceof Inventory && swatch.item().test(slot.getItem())) {
				from = slot.index;
				break;
			}
		}
		if (from < 0) {
			deny();
			return;
		}
		if (!inSlot.isEmpty()) {
			click(CartographyTableMenu.ADDITIONAL_SLOT, LEFT_CLICK, ContainerInput.QUICK_MOVE);
		}
		click(from, LEFT_CLICK, ContainerInput.QUICK_MOVE);
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DYE_USE, 1.2F, 0.5F));
	}

	private void extractCanvas(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		// A slot-style frame, as vanilla frames its map preview.
		graphics.fill(x - 1, y - 1, x + Ink.SIZE, y, SLOT_DARK);
		graphics.fill(x - 1, y, x, y + Ink.SIZE, SLOT_DARK);
		graphics.fill(x, y + Ink.SIZE, x + Ink.SIZE + 1, y + Ink.SIZE + 1, SLOT_LIGHT);
		graphics.fill(x + Ink.SIZE, y, x + Ink.SIZE + 1, y + Ink.SIZE, SLOT_LIGHT);
		if (!hasCanvas()) {
			graphics.fill(x, y, x + Ink.SIZE, y + Ink.SIZE, SLOT_INSIDE);
			return;
		}
		if (sheetShown()) {
			extractSheet(graphics, x, y, mouseX, mouseY);
			return;
		}

		// The paper a held map is drawn on, then the map, its pins and its labels, as in item frames.
		graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_BACKGROUND, x, y, 7, 7, Ink.SIZE, Ink.SIZE, Ink.SIZE, Ink.SIZE, 142, 142);
		MapId mapId = shownMap();
		MapItemSavedData data = mapData(mapId);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (data != null) {
			minecraft.getMapRenderer().extractRenderState(mapId, data, mapRenderState);
			graphics.map(mapRenderState);
		}
		extractEdits(graphics);
		graphics.pose().popMatrix();

		preview.extract(graphics, x, y);
		InkMaterial material = material();
		if (material != null && !shape.isEmpty()) {
			InkPreview.drawRuns(graphics, x, y, shape, InkPreview.previewColor(material, strength));
		}

		InkTool tool = tool();
		if (sealed()) {
			graphics.item(new ItemStack(Items.HONEYCOMB), x + Ink.SIZE - 18, y + 2);
		} else if (canDraw(tool) && overCanvas(mouseX, mouseY) && stroke == null && tool != InkTool.FILL) {
			// The tool's tip under the cursor.
			BitSet cursor = new BitSet(Ink.PIXELS);
			Strokes.dab(cursor, pixelX(mouseX), pixelY(mouseY), tool.size());
			int color = InkPreview.previewColor(material, strength);
			InkPreview.drawRuns(graphics, x, y, cursor, (color & 0x00FFFFFF) | Math.round((color >>> 24) * 0.6F) << 24);
		}
	}

	/** The sheet: each map on its paper at a third of its size, the table's own framed, and the strokes crossing them. */
	private void extractSheet(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		int[] sheet = sheet();
		graphics.fill(x, y, x + Ink.SIZE, y + Ink.SIZE, SLOT_INSIDE);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(1.0F / SHEET, 1.0F / SHEET);
		for (int i = 0; i < sheet.length; i++) {
			if (sheet[i] < 0) {
				continue;
			}
			int tileX = i % SHEET * Ink.SIZE;
			int tileY = i / SHEET * Ink.SIZE;
			graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_BACKGROUND, tileX, tileY, 7, 7, Ink.SIZE, Ink.SIZE, Ink.SIZE, Ink.SIZE, 142, 142);
			MapId mapId = new MapId(sheet[i]);
			MapItemSavedData data = mapData(mapId);
			if (data != null) {
				graphics.pose().pushMatrix();
				graphics.pose().translate(tileX, tileY);
				minecraft.getMapRenderer().extractRenderState(mapId, data, sheetStates[i]);
				graphics.map(sheetStates[i]);
				graphics.pose().popMatrix();
			}
		}
		sheetPreview.extract(graphics, 0, 0);
		InkMaterial material = material();
		if (material != null && !sheetShape.isEmpty()) {
			InkPreview.drawRuns(graphics, 0, 0, sheetShape, InkPreview.previewColor(material, strength), SHEET_WIDTH);
		}
		graphics.pose().popMatrix();

		int over = overCanvas(mouseX, mouseY) ? tile(sheetX(mouseX), sheetY(mouseY)) : -1;
		for (int i = 0; i < sheet.length; i++) {
			int x0 = x + Math.round(i % SHEET * Ink.SIZE / (float) SHEET);
			int y0 = y + Math.round(i / SHEET * Ink.SIZE / (float) SHEET);
			int x1 = x + Math.round((i % SHEET + 1) * Ink.SIZE / (float) SHEET);
			int y1 = y + Math.round((i / SHEET + 1) * Ink.SIZE / (float) SHEET);
			if (i == MIDDLE) {
				// The map on the table, framed.
				graphics.fill(x0, y0, x1, y0 + 1, SLOT_LIGHT);
				graphics.fill(x0, y1 - 1, x1, y1, SLOT_LIGHT);
				graphics.fill(x0, y0, x0 + 1, y1, SLOT_LIGHT);
				graphics.fill(x1 - 1, y0, x1, y1, SLOT_LIGHT);
			} else if (i == over && sheet[i] >= 0 && tool() == null && stroke == null) {
				// A map an empty hand would put on the table.
				graphics.fill(x0, y0, x1, y1, 0x40FFFFFF);
			}
			if (sheet[i] >= 0 && sealedOnSheet(i)) {
				graphics.item(new ItemStack(Items.HONEYCOMB), x1 - 17, y0 + 1);
			}
		}
	}

	/** Marks being typed, dragged or on their way to the server, drawn on the map at the pose's origin. */
	private void extractEdits(GuiGraphicsExtractor graphics) {
		if (pending != null) {
			MarkRenderer.draw(graphics, pending);
		}
		if (dragged != null) {
			// Shown where it's being dragged, or where it was while the mouse hasn't moved yet.
			int x = dragMoved ? Mark.clampPixel(dragged.x() + dragX - pressX) : dragged.x();
			int y = dragMoved ? Mark.clampPixel(dragged.y() + dragY - pressY) : dragged.y();
			MarkRenderer.draw(graphics, dragged.at(x, y));
		}
		if (editing != null) {
			DyeColor ink = labelInk();
			int color = Labels.color(ink != null ? ink : DyeColor.BLACK);
			String text = editing.text.toString();
			boolean caret = ticks / 6 % 2 == 0;
			Labels.draw(graphics, caret ? text + "_" : text + " ", editing.x, editing.y, editing.size, color);
		}
	}

	/** Drawn over the slots' items: the ink left in the second slot's item, and tooltips. */
	public void extractForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		InkMaterial material = material();
		InkStatePayload state = DeskState.get();
		if (material != null && !state.free() && state.charge() > 0) {
			// Like a durability bar: what's left of the item being used up.
			int x = left() + 15 + 2;
			int y = top() + 52 + 13;
			float left = Math.min(1.0F, state.charge() / (float) Math.max(1, state.perItem()));
			int width = Math.round(13 * left);
			graphics.fill(x, y, x + 13, y + 2, 0xFF000000);
			graphics.fill(x, y, x + width, y + 1, barColor(left));
			// The stack count stays readable on top, as over a durability bar.
			ItemStack stack = menu().getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem();
			graphics.itemDecorations(minecraft.font, stack, left() + 15, top() + 52, null);
		}

		// Hints go under the window, never over the map.
		Component status = null;
		if (editing != null) {
			status = Component.translatable("mapplus.desk.label.size", sizeName(editing.size)).append(" · ")
					.append(Component.translatable("mapplus.desk.label.keys"));
		} else if (strengthShown > 0 && material() != null) {
			status = strengthName(material());
		} else if (overCanvas(mouseX, mouseY) && stroke == null && dragged == null) {
			status = canvasHint(pixelX(mouseX), pixelY(mouseY));
		}
		if (status != null) {
			List<net.minecraft.util.FormattedCharSequence> lines = minecraft.font.split(status, WIDTH - 12);
			int y = top() + HEIGHT + 4;
			if (y + lines.size() * 10 > screen.height) {
				y = top() - 4 - lines.size() * 10;
			}
			for (net.minecraft.util.FormattedCharSequence line : lines) {
				graphics.text(minecraft.font, line, left() + (WIDTH - minecraft.font.width(line)) / 2, y, 0xFFFFFFFF, true);
				y += 10;
			}
		}
		if (over(mouseX, mouseY, ZOOM_X, RACK_Y, ZOOM_WIDTH, BUTTON_HEIGHT)) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable(sheetShown() ? "mapplus.desk.sheet.in" : "mapplus.desk.sheet.out"));
			lines.add(Component.translatable("mapplus.desk.sheet.about").withStyle(ChatFormatting.GRAY));
			if (!canLayOut() && !sheetShown()) {
				lines.add(Component.translatable("mapplus.desk.sheet.none").withStyle(ChatFormatting.RED));
			} else {
				lines.add(Component.translatable("mapplus.desk.sheet.keys").withStyle(ChatFormatting.DARK_GRAY));
			}
			graphics.setTooltipForNextFrame(minecraft.font, lines, Optional.empty(), mouseX, mouseY);
		}
		for (int i = 0; i < PALETTE.size(); i++) {
			if (over(mouseX, mouseY, PALETTE_X, CANVAS_Y + i * SWATCH, SWATCH, SWATCH)) {
				Swatch swatch = PALETTE.get(i);
				int count = count(swatch);
				graphics.setTooltipForNextFrame(minecraft.font, List.of(swatch.name(), (count > 0
						? Component.translatable("mapplus.desk.palette.count", count)
						: Component.translatable("mapplus.desk.rack.none")).withStyle(count > 0 ? ChatFormatting.GRAY : ChatFormatting.RED)),
						Optional.empty(), mouseX, mouseY);
			}
		}
		InkTool carried = tool();
		for (int i = 0; i < RACK.length; i++) {
			if (over(mouseX, mouseY, rackX(i), RACK_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
				InkTool tool = RACK[i];
				List<Component> lines = new ArrayList<>();
				lines.add(tool.title());
				lines.add(tool.description().copy().withStyle(ChatFormatting.GRAY));
				if (!unlocked(tool)) {
					lines.add(Component.translatable("mapplus.desk.rack.loom").withStyle(ChatFormatting.RED));
				} else if (tool == carried) {
					lines.add(Component.translatable("mapplus.desk.rack.put_back").withStyle(ChatFormatting.DARK_GRAY));
				} else if (find(tool) == null) {
					lines.add(Component.translatable("mapplus.desk.rack.none").withStyle(ChatFormatting.RED));
				}
				graphics.setTooltipForNextFrame(minecraft.font, lines, Optional.empty(), mouseX, mouseY);
			}
		}
	}

	private Component canvasHint(int x, int y) {
		if (!hasCanvas()) {
			return Component.translatable("mapplus.desk.hint.map");
		}
		if (sheetShown()) {
			InkTool tool = tool();
			if (!DeskState.get().hasNeighbors()) {
				return Component.translatable("mapplus.desk.hint.sheet_empty");
			}
			if (tool == null) {
				return Component.translatable("mapplus.desk.hint.sheet");
			}
			if (!tool.draws() || tool == InkTool.FILL) {
				return Component.translatable("mapplus.desk.hint.sheet_zoom");
			}
			return material() == null ? Component.translatable("mapplus.desk.hint.material") : null;
		}
		if (sealed()) {
			return Component.translatable(DeskState.get().scrapable() ? "mapplus.desk.hint.sealed" : "mapplus.desk.hint.sealed_by_other");
		}
		InkTool tool = tool();
		if (tool == null) {
			return Component.translatable(DeskState.get().loom() ? "mapplus.desk.hint.tool_loom" : "mapplus.desk.hint.tool");
		}
		if (!unlocked(tool)) {
			return Component.translatable("mapplus.desk.hint.loom");
		}
		if (tool == InkTool.LABEL) {
			if (markAt(x, y, Mark.Kind.LABEL) != null) {
				return Component.translatable("mapplus.desk.hint.label_edit");
			}
			return labelInk() == null ? Component.translatable("mapplus.desk.hint.label_ink") : null;
		}
		if (tool == InkTool.PIN) {
			if (markAt(x, y, Mark.Kind.PIN) != null) {
				return Component.translatable("mapplus.desk.hint.pin_edit");
			}
			return shownMap() == null ? Component.translatable("mapplus.desk.hint.pin_canvas") : null;
		}
		if (tool == InkTool.ICON) {
			if (markAt(x, y, Mark.Kind.ICON) != null) {
				return Component.translatable("mapplus.desk.hint.icon_edit");
			}
			return labelInk() == null ? Component.translatable("mapplus.desk.hint.icon_ink") : null;
		}
		if (material() == null) {
			return Component.translatable("mapplus.desk.hint.material");
		}
		return null;
	}

	private static Component sizeName(int size) {
		return Component.translatable(switch (Mark.clampSize(size)) {
			case Mark.SMALL -> "mapplus.desk.size.small";
			case Mark.LARGE -> "mapplus.desk.size.large";
			default -> "mapplus.desk.size.medium";
		});
	}

	/** How the ink in the second slot is drawn at the strength picked: how much of its color, or what water does. */
	private static Component strengthName(InkMaterial material) {
		if (material.kind() == InkMaterial.Kind.WATER) {
			return Component.translatable(strength == Ink.FULL ? "mapplus.desk.strength.erase" : "mapplus.desk.strength.fade");
		}
		return Component.translatable("mapplus.desk.strength." + strength);
	}

	private static int barColor(float left) {
		int red = left < 0.5F ? 255 : Math.round(255 * (1 - left) * 2);
		int green = left > 0.5F ? 255 : Math.round(255 * left * 2);
		return 0xFF000000 | red << 16 | green << 8;
	}

	// The rack

	/** The player's own item for a tool, if they have one: carried, in the inventory or in the off hand. */
	private ItemStack find(InkTool tool) {
		if (tool.matches(menu().getCarried())) {
			return menu().getCarried();
		}
		int slot = inventorySlot(tool);
		if (slot >= 0) {
			return menu().getSlot(slot).getItem();
		}
		ItemStack offHand = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getOffhandItem();
		return tool.matches(offHand) ? offHand : null;
	}

	private ItemStack icon(InkTool tool) {
		ItemStack own = find(tool);
		return own != null ? own : tool.icon();
	}

	private int inventorySlot(InkTool tool) {
		for (Slot slot : menu().slots) {
			if (slot.container instanceof Inventory && tool.matches(slot.getItem())) {
				return slot.index;
			}
		}
		return -1;
	}

	private int emptyInventorySlot() {
		for (Slot slot : menu().slots) {
			if (slot.container instanceof Inventory && !slot.hasItem()) {
				return slot.index;
			}
		}
		return -1;
	}

	/** Picks a tool up from the inventory, as clicking its slot would; clicking the tool in hand puts it back. */
	private void pickUp(InkTool tool) {
		if (!unlocked(tool)) {
			deny();
			return;
		}
		if (tool.matches(menu().getCarried())) {
			int empty = emptyInventorySlot();
			if (empty < 0) {
				deny();
				return;
			}
			click(empty, LEFT_CLICK, ContainerInput.PICKUP);
			return;
		}
		ItemStack carried = menu().getCarried();
		if (!carried.isEmpty() && InkTool.of(carried) == null) {
			// Anything else in hand is put down first, so it can't take the tool in, as a bundle would.
			int empty = emptyInventorySlot();
			if (empty < 0) {
				deny();
				return;
			}
			click(empty, LEFT_CLICK, ContainerInput.PICKUP);
		}
		int slot = inventorySlot(tool);
		if (slot >= 0) {
			click(slot, LEFT_CLICK, ContainerInput.PICKUP);
			return;
		}
		// A tool held in the off hand comes out through an empty slot, as the swap key would move it.
		int empty = emptyInventorySlot();
		if (minecraft.player != null && tool.matches(minecraft.player.getOffhandItem()) && empty >= 0) {
			click(empty, OFF_HAND_BUTTON, ContainerInput.SWAP);
			click(empty, LEFT_CLICK, ContainerInput.PICKUP);
			return;
		}
		deny();
	}

	private void click(int slot, int button, ContainerInput input) {
		if (minecraft.gameMode != null && minecraft.player != null) {
			minecraft.gameMode.handleContainerInput(menu().containerId, slot, button, input, minecraft.player);
		}
	}

	// Input

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!overWing(mouseX, mouseY)) {
			if (editing != null) {
				commitLabel();
			}
			return false;
		}
		// A press on the desk keeps its drag and release, even if they end outside the window, so a tool is never thrown.
		pressButton = button;
		if (button == InputConstants.MOUSE_BUTTON_RIGHT && editing == null && overCanvas(mouseX, mouseY)) {
			// The right button erases with any drawing tool, as the other end of a pencil.
			InkTool tool = tool();
			if (sheetShown()) {
				if (drawsOnSheet(tool)) {
					beginSheet(tool, sheetX(mouseX), sheetY(mouseY), true);
				}
			} else if (canErase(tool)) {
				begin(tool, pixelX(mouseX), pixelY(mouseY), true);
			}
			return true;
		}
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return true;
		}
		for (int i = 0; i < PALETTE.size(); i++) {
			if (over(mouseX, mouseY, PALETTE_X, CANVAS_Y + i * SWATCH, SWATCH, SWATCH)) {
				loadInk(PALETTE.get(i));
				return true;
			}
		}
		for (int i = 0; i < RACK.length; i++) {
			if (over(mouseX, mouseY, rackX(i), RACK_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
				if (editing != null) {
					commitLabel();
				}
				pickUp(RACK[i]);
				return true;
			}
		}
		if (over(mouseX, mouseY, ZOOM_X, RACK_Y, ZOOM_WIDTH, BUTTON_HEIGHT)) {
			if (sheetShown() || canLayOut()) {
				setZoomedOut(!sheetShown());
			} else {
				deny();
			}
			return true;
		}
		if (editing != null) {
			// Clicking anywhere else finishes the label being typed.
			commitLabel();
			return true;
		}
		if (!overCanvas(mouseX, mouseY)) {
			return true;
		}
		if (sheetShown()) {
			clickSheet(mouseX, mouseY);
			return true;
		}
		InkTool tool = tool();
		if (tool == null || !hasCanvas()) {
			return true;
		}
		if (!unlocked(tool)) {
			deny();
			return true;
		}
		int x = pixelX(mouseX);
		int y = pixelY(mouseY);
		switch (tool) {
			case PEN, BRUSH, LINE, BOX, BORDER, FILL -> {
				if (canDraw(tool)) {
					begin(tool, x, y, false);
				}
			}
			case LABEL -> {
				Mark hit = markAt(x, y, Mark.Kind.LABEL);
				if (sealed()) {
					deny();
				} else if (hit != null) {
					startDrag(hit, x, y);
				} else if (canWrite()) {
					startEditing(new LabelEdit(null, x, y, Mark.MEDIUM, ""));
				} else {
					deny();
				}
			}
			case PIN -> {
				Mark hit = markAt(x, y, Mark.Kind.PIN);
				if (sealed()) {
					deny();
				} else if (hit != null) {
					startDrag(hit, x, y);
				} else if (canPin()) {
					sendMark(MarkEditPayload.Action.PIN, -1, x, y, Mark.MEDIUM, "");
				} else {
					deny();
				}
			}
			case ICON -> {
				Mark hit = markAt(x, y, Mark.Kind.ICON);
				if (sealed()) {
					deny();
				} else if (hit != null) {
					startDrag(hit, x, y);
				} else if (canStamp() && affordable(Mark.iconCost(Mark.MEDIUM), material())) {
					sendMark(MarkEditPayload.Action.ICON, -1, x, y, Mark.MEDIUM, "");
				} else {
					deny();
				}
			}
		}
		return true;
	}

	/** Starts a stroke with a drawing tool; Shift-clicking with the pen draws a straight line on from where the last one ended. */
	private void begin(InkTool tool, int x, int y, boolean erase) {
		erasing = erase;
		if (tool == InkTool.FILL) {
			fill(x, y);
			return;
		}
		stroke = tool;
		if (tool == InkTool.PEN || tool == InkTool.BRUSH) {
			penStroke.clear();
			boolean onFrom = minecraft.hasShiftDown() && penEndX >= 0;
			lastX = onFrom ? penEndX : x;
			lastY = onFrom ? penEndY : y;
			penDab(lastX, lastY, x, y);
			lastX = x;
			lastY = y;
		} else {
			anchorX = x;
			anchorY = y;
			reshape(shape, Ink.SIZE, x, y);
		}
	}

	/**
	 * A click on the sheet: with an empty hand, puts the map clicked on the
	 * table (or zooms back in on the table's own); with a drawing tool, starts a
	 * stroke across the maps. Fills, labels, pins and icons work on one map.
	 */
	private void clickSheet(double mouseX, double mouseY) {
		int x = sheetX(mouseX);
		int y = sheetY(mouseY);
		int place = tile(x, y);
		InkTool tool = tool();
		if (tool == null) {
			int mapId = sheet()[place];
			if (place == MIDDLE) {
				setZoomedOut(false);
			} else if (mapId >= 0) {
				putOnTable(mapId);
			} else {
				deny();
			}
			return;
		}
		if (drawsOnSheet(tool) && material() != null) {
			beginSheet(tool, x, y, false);
		} else {
			deny();
		}
	}

	/** Whether a tool draws across the sheet: the pen, brush, string, frame and chain. */
	private boolean drawsOnSheet(InkTool tool) {
		return tool != null && tool.draws() && tool != InkTool.FILL && unlocked(tool);
	}

	private void beginSheet(InkTool tool, int x, int y, boolean erase) {
		erasing = erase;
		stroke = tool;
		onSheet = true;
		if (tool == InkTool.PEN || tool == InkTool.BRUSH) {
			sheetPenStroke.clear();
			lastX = x;
			lastY = y;
			sheetDab(x, y, x, y);
		} else {
			anchorX = x;
			anchorY = y;
			reshape(sheetShape, SHEET_WIDTH, x, y);
		}
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button) {
		if (pressButton < 0 || button != pressButton) {
			return false;
		}
		if (dragged != null) {
			dragX = pixelX(mouseX);
			dragY = pixelY(mouseY);
			dragMoved |= Math.abs(dragX - pressX) > 1 || Math.abs(dragY - pressY) > 1;
			return true;
		}
		if (stroke == null) {
			return true;
		}
		if (onSheet) {
			int x = Math.clamp(sheetX(mouseX), -SHEET_WIDTH, SHEET_WIDTH * 2);
			int y = Math.clamp(sheetY(mouseY), -SHEET_WIDTH, SHEET_WIDTH * 2);
			if (stroke == InkTool.PEN || stroke == InkTool.BRUSH) {
				sheetDab(lastX, lastY, x, y);
				lastX = x;
				lastY = y;
			} else {
				reshape(sheetShape, SHEET_WIDTH, x, y);
			}
			return true;
		}
		// Far enough out to cover any drag, close enough that lines stay cheap.
		int x = Math.clamp(pixelX(mouseX), -Ink.SIZE, Ink.SIZE * 2);
		int y = Math.clamp(pixelY(mouseY), -Ink.SIZE, Ink.SIZE * 2);
		if (stroke == InkTool.PEN || stroke == InkTool.BRUSH) {
			penDab(lastX, lastY, x, y);
			lastX = x;
			lastY = y;
		} else {
			reshape(shape, Ink.SIZE, x, y);
		}
		return true;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (pressButton < 0 || button != pressButton) {
			return false;
		}
		pressButton = -1;
		if (dragged != null) {
			finishDrag(mouseX, mouseY);
			return true;
		}
		if (stroke == null) {
			return true;
		}
		InkTool tool = stroke;
		stroke = null;
		if (onSheet) {
			onSheet = false;
			if (tool == InkTool.PEN || tool == InkTool.BRUSH) {
				flushSheetPen();
			} else if (!sheetShape.isEmpty()) {
				BitSet pixels = (BitSet) sheetShape.clone();
				sheetShape.clear();
				sendSheet(pixels, tool);
			}
			return true;
		}
		if (tool == InkTool.PEN || tool == InkTool.BRUSH) {
			flushPen();
			penEndX = lastX;
			penEndY = lastY;
		} else if (!shape.isEmpty()) {
			BitSet pixels = (BitSet) shape.clone();
			shape.clear();
			send(pixels, true, tool);
		}
		return true;
	}

	/**
	 * Scrolling over the map: while typing a label, sizes it; over a mark with
	 * its tool, sizes that mark; with a drawing tool, waters the ink down or
	 * makes it stronger.
	 */
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (editing == null && stroke == null && overCanvas(mouseX, mouseY) && minecraft.hasControlDown()) {
			// Ctrl and the wheel zoom out to the sheet of maps and back in.
			if (amount < 0 && canLayOut()) {
				setZoomedOut(true);
			} else if (amount > 0) {
				setZoomedOut(false);
			}
			return true;
		}
		if (editing == null && !(overCanvas(mouseX, mouseY) && hasCanvas() && tool() != null)) {
			return false;
		}
		// Touchpads scroll in small steps; change once per whole step.
		if (Math.signum(amount) != Math.signum(scrolled)) {
			scrolled = 0;
		}
		scrolled += amount;
		int steps = (int) scrolled;
		if (steps == 0) {
			return true;
		}
		scrolled -= steps;
		if (editing != null) {
			resizeLabel(steps);
			return true;
		}
		InkTool tool = tool();
		if (tool.draws()) {
			// Scrolling up makes the ink stronger, down waters it down.
			strength = Ink.clampStrength(strength - steps);
			strengthShown = STRENGTH_SHOWN_TICKS;
			return true;
		}
		if (sheetShown()) {
			return true;
		}
		Mark mark = markAt(pixelX(mouseX), pixelY(mouseY), tool.mark());
		if (mark != null && unlocked(tool) && !sealed()) {
			int size = Mark.clampSize(mark.size() + steps);
			if (size != mark.size() && (!mark.isLabel() || Labels.width(mark.text(), size) <= Labels.MAX_WIDTH)) {
				Mark resized = mark.withSize(size);
				int cost = Math.max(0, resized.cost() - mark.cost());
				if (cost > 0 && (labelInk() != mark.color() || !affordable(cost, material()))) {
					deny();
					return true;
				}
				sendMark(MarkEditPayload.Action.RESIZE, mark.id(), mark.x(), mark.y(), size, "");
				pending = resized;
				pendingHidden = mark.id();
				ClientMarks.hide(shownMap(), mark.id());
			}
		}
		return true;
	}

	private void resizeLabel(int steps) {
		int size = Mark.clampSize(editing.size + steps);
		// A label never grows wider than the map.
		if (Labels.width(editing.text.toString(), size) <= Labels.MAX_WIDTH) {
			editing.size = size;
		}
	}

	/** While a label is being typed, every key goes to it, so E and the number keys type letters. */
	public boolean keyPressed(KeyEvent event) {
		if (editing == null) {
			return false;
		}
		switch (event.key()) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> commitLabel();
			case InputConstants.KEY_ESCAPE -> cancelLabel();
			case InputConstants.KEY_UP -> resizeLabel(1);
			case InputConstants.KEY_DOWN -> resizeLabel(-1);
			case InputConstants.KEY_BACKSPACE -> {
				int length = editing.text.length();
				if (length > 0) {
					editing.text.setLength(editing.text.offsetByCodePoints(length, -1));
				}
			}
			default -> {
			}
		}
		return true;
	}

	public boolean charTyped(CharacterEvent event) {
		if (editing == null) {
			return false;
		}
		String typed = editing.text.toString();
		String longer = typed + Character.toString(event.codepoint());
		if (event.isAllowedChatCharacter() && typed.codePointCount(0, typed.length()) < Mark.MAX_TEXT
				&& Labels.width(longer, editing.size) <= Labels.MAX_WIDTH) {
			editing.text.appendCodePoint(event.codepoint());
		}
		return true;
	}

	public void tick() {
		ticks++;
		// Pen strokes go out as they're drawn, a tick's worth at a time.
		flushPen();
		flushSheetPen();
		preview.acknowledge(DeskState.get().ack());
		sheetPreview.acknowledge(DeskState.get().ack());
		if (scratchTicks > 0) {
			scratchTicks--;
		}
		if (stroke != null && (onSheet ? !sheetShown() || !drawsOnSheet(stroke) || !erasing && material() == null
				: !(erasing ? canErase(stroke) : canDraw(stroke)))) {
			stroke = null;
			onSheet = false;
			shape.clear();
			sheetShape.clear();
			penPixels.clear();
			sheetPen.clear();
		}
		if (editing != null && (tool() != InkTool.LABEL || !canWrite())) {
			cancelLabel();
		}
		if (strengthShown > 0) {
			strengthShown--;
		}
		if (dragged != null && tool() != InkTool.of(dragged.kind())) {
			dragged = null;
			ClientMarks.unhide();
		}
		if (pendingHidden >= 0 || pending != null) {
			boolean answered = DeskState.get().ack() >= pendingSeq && ClientMarks.updates(pendingMap) != pendingUpdates;
			if (answered || --pendingTicks <= 0) {
				pending = null;
				pendingHidden = -1;
				if (editing == null && dragged == null) {
					ClientMarks.unhide();
				}
			}
		}
	}

	public void acknowledge(int ack) {
		preview.acknowledge(ack);
		sheetPreview.acknowledge(ack);
	}

	/** Sends what the pen drew, before the window tells the server it closed. */
	public void close() {
		flushPen();
		flushSheetPen();
		pressButton = -1;
		stroke = null;
		onSheet = false;
		shape.clear();
		sheetShape.clear();
		if (editing != null) {
			stopEditing();
		}
		dragged = null;
		ClientMarks.unhide();
	}

	// Strokes

	private void penDab(int x0, int y0, int x1, int y1) {
		BitSet pixels = new BitSet(Ink.PIXELS);
		Strokes.line(pixels, x0, y0, x1, y1, stroke.size());
		pixels.andNot(penStroke);
		penStroke.or(pixels);
		if (pixels.isEmpty()) {
			return;
		}
		if (penSeq == 0) {
			penSeq = DeskState.nextSeq();
		}
		penPixels.or(pixels);
		InkMaterial material = strokeMaterial();
		if (material != null) {
			preview.add(pixels, penSeq, InkPreview.previewColor(material, strokeStrength()));
		}
		// The scratch of a pen on paper, now and then while it moves.
		if (scratchTicks <= 0) {
			scratchTicks = 7;
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN,
					1.6F + minecraft.level.getRandom().nextFloat() * 0.3F, 0.12F));
		}
	}

	/** What the stroke being drawn lays down: the second slot's ink, or nothing when erasing. */
	private InkMaterial strokeMaterial() {
		return erasing ? InkMaterial.WATER : material();
	}

	private int strokeStrength() {
		return erasing ? Ink.FULL : strength;
	}

	private void flushPen() {
		if (penPixels.isEmpty()) {
			return;
		}
		ClientPlayNetworking.send(new InkStrokePayload(penSeq, target(), false, strokeStrength(), erasing, (BitSet) penPixels.clone()));
		penPixels.clear();
		penSeq = 0;
	}

	/** Lays out the line, box or border being dragged from the anchor to a point, on a map or on the sheet. */
	private void reshape(BitSet shape, int width, int x, int y) {
		shape.clear();
		// Shift keeps lines straight, at right angles or diagonal, and boxes square.
		if (minecraft.hasShiftDown()) {
			int dx = x - anchorX;
			int dy = y - anchorY;
			if (stroke == InkTool.BOX) {
				int side = Math.max(Math.abs(dx), Math.abs(dy));
				x = anchorX + (dx < 0 ? -side : side);
				y = anchorY + (dy < 0 ? -side : side);
			} else if (Math.abs(dx) > 2 * Math.abs(dy)) {
				y = anchorY;
			} else if (Math.abs(dy) > 2 * Math.abs(dx)) {
				x = anchorX;
			} else {
				int side = Math.max(Math.abs(dx), Math.abs(dy));
				x = anchorX + (dx < 0 ? -side : side);
				y = anchorY + (dy < 0 ? -side : side);
			}
		}
		switch (stroke) {
			case LINE -> Strokes.line(shape, anchorX, anchorY, x, y, 1, width);
			case BOX -> Strokes.box(shape, Math.min(anchorX, x), Math.min(anchorY, y), Math.max(anchorX, x), Math.max(anchorY, y), 1, width);
			case BORDER -> Strokes.dashed(shape, anchorX, anchorY, x, y, DASH, GAP, width);
			default -> {
			}
		}
	}

	/** Fills the area around a pixel, bounded by ink: inside a border, or one color of ink. */
	private void fill(int x, int y) {
		byte[] ink = DeskState.ink(target());
		if (ink == null) {
			// The map's ink hasn't arrived yet; filling now could flood past its lines.
			return;
		}
		BitSet pixels = new BitSet(Ink.PIXELS);
		Strokes.fill(pixels, ink, preview.pending(), x, y);
		send(pixels, true, InkTool.FILL);
	}

	/** Sends a finished stroke and shows it until the server has drawn it, with a sound for what it does. */
	private void send(BitSet pixels, boolean whole, InkTool tool) {
		InkMaterial material = strokeMaterial();
		if (material == null || pixels.isEmpty()) {
			return;
		}
		if (whole && !erasing && !affordable((pixels.cardinality() * material.quarters(strength) + 3) / 4, material)) {
			deny();
			return;
		}
		int seq = DeskState.nextSeq();
		preview.add(pixels, seq, InkPreview.previewColor(material, strokeStrength()));
		ClientPlayNetworking.send(new InkStrokePayload(seq, target(), whole, strokeStrength(), erasing, pixels));
		SoundEvent sound = erasing || material.kind() == InkMaterial.Kind.WATER ? SoundEvents.BOTTLE_EMPTY
				: material.kind() == InkMaterial.Kind.GLOW ? SoundEvents.GLOW_INK_SAC_USE
				: tool == InkTool.FILL ? SoundEvents.BUCKET_EMPTY
				: SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT;
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.1F, 0.6F));
	}

	/** The pen or brush moving across the sheet: its pixels, on maps that can be drawn on, go out as they're drawn. */
	private void sheetDab(int x0, int y0, int x1, int y1) {
		BitSet pixels = new BitSet(SHEET_WIDTH * SHEET_WIDTH);
		Strokes.line(pixels, x0, y0, x1, y1, stroke.size(), SHEET_WIDTH);
		pixels.andNot(sheetPenStroke);
		sheetPenStroke.or(pixels);
		pixels.and(drawable());
		if (pixels.isEmpty()) {
			return;
		}
		if (penSeq == 0) {
			penSeq = DeskState.nextSeq();
		}
		sheetPen.or(pixels);
		InkMaterial material = strokeMaterial();
		if (material != null) {
			sheetPreview.add(pixels, penSeq, InkPreview.previewColor(material, strokeStrength()));
		}
		if (scratchTicks <= 0) {
			scratchTicks = 7;
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN,
					1.6F + minecraft.level.getRandom().nextFloat() * 0.3F, 0.12F));
		}
	}

	private void flushSheetPen() {
		if (sheetPen.isEmpty()) {
			return;
		}
		for (java.util.Map.Entry<Integer, BitSet> part : split(sheetPen).entrySet()) {
			ClientPlayNetworking.send(new InkStrokePayload(penSeq, part.getKey(), false, strokeStrength(), erasing, part.getValue()));
		}
		sheetPen.clear();
		penSeq = 0;
	}

	/** Sends a finished line, box or border on the sheet: each map it crosses gets its part, drawn whole. */
	private void sendSheet(BitSet pixels, InkTool tool) {
		InkMaterial material = strokeMaterial();
		pixels.and(drawable());
		if (material == null || pixels.isEmpty()) {
			return;
		}
		if (!erasing && !affordable((pixels.cardinality() * material.quarters(strength) + 3) / 4, material)) {
			deny();
			return;
		}
		int seq = DeskState.nextSeq();
		sheetPreview.add(pixels, seq, InkPreview.previewColor(material, strokeStrength()));
		for (java.util.Map.Entry<Integer, BitSet> part : split(pixels).entrySet()) {
			ClientPlayNetworking.send(new InkStrokePayload(seq, part.getKey(), true, strokeStrength(), erasing, part.getValue()));
		}
		SoundEvent sound = erasing || material.kind() == InkMaterial.Kind.WATER ? SoundEvents.BOTTLE_EMPTY
				: material.kind() == InkMaterial.Kind.GLOW ? SoundEvents.GLOW_INK_SAC_USE
				: SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT;
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.1F, 0.6F));
	}

	/** The sheet pixels a stroke can draw on: those of maps on the sheet that aren't sealed. */
	private BitSet drawable() {
		int[] sheet = sheet();
		BitSet drawable = new BitSet(SHEET_WIDTH * SHEET_WIDTH);
		for (int i = 0; i < sheet.length; i++) {
			if (sheet[i] < 0 || sealedOnSheet(i)) {
				continue;
			}
			int tileX = i % SHEET * Ink.SIZE;
			int tileY = i / SHEET * Ink.SIZE;
			for (int row = 0; row < Ink.SIZE; row++) {
				int start = tileX + (tileY + row) * SHEET_WIDTH;
				drawable.set(start, start + Ink.SIZE);
			}
		}
		return drawable;
	}

	/** A stroke on the sheet, cut into the part on each map it crosses, by map. */
	private java.util.Map<Integer, BitSet> split(BitSet pixels) {
		int[] sheet = sheet();
		java.util.Map<Integer, BitSet> parts = new java.util.LinkedHashMap<>();
		for (int i = pixels.nextSetBit(0); i >= 0 && i < SHEET_WIDTH * SHEET_WIDTH; i = pixels.nextSetBit(i + 1)) {
			int x = i % SHEET_WIDTH;
			int y = i / SHEET_WIDTH;
			int mapId = sheet[tile(x, y)];
			if (mapId >= 0) {
				parts.computeIfAbsent(mapId, id -> new BitSet(Ink.PIXELS)).set(x % Ink.SIZE + y % Ink.SIZE * Ink.SIZE);
			}
		}
		return parts;
	}

	/** A rough check before sending, so a shape that can't be paid for doesn't flash on the canvas. */
	private boolean affordable(int pixels, InkMaterial material) {
		InkStatePayload state = DeskState.get();
		// Glow ink and water only cost on inked pixels, which the client can't tell apart.
		if (state.free() || material.kind() != InkMaterial.Kind.DYE) {
			return true;
		}
		ItemStack stack = menu().getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem();
		return InkManager.available(stack.getCount(), state.charge(), state.perItem()) >= pixels;
	}

	private int target() {
		MapId mapId = shownMap();
		return mapId == null ? InkStrokePayload.NEW_CANVAS : mapId.id();
	}

	// Labels and pins

	/** The label or pin of this kind under a map pixel, the topmost one. */
	private Mark markAt(int x, int y, Mark.Kind kind) {
		List<Mark> marks = ClientMarks.get(shownMap());
		for (int i = marks.size() - 1; i >= 0; i--) {
			Mark mark = marks.get(i);
			if (mark.kind() != kind) {
				continue;
			}
			float halfWidth = Math.max(2, MarkRenderer.width(mark) / 2);
			float halfHeight = Math.max(2, MarkRenderer.height(mark) / 2);
			if (Math.abs(x - mark.x()) <= halfWidth && Math.abs(y - mark.y()) <= halfHeight) {
				return mark;
			}
		}
		return null;
	}

	private void startDrag(Mark mark, int x, int y) {
		dragged = mark;
		pressX = x;
		pressY = y;
		dragX = x;
		dragY = y;
		dragMoved = false;
		ClientMarks.hide(shownMap(), mark.id());
	}

	/** Dropping a mark moves it; dropping it off the map removes it; clicking a label rewrites it. */
	private void finishDrag(double mouseX, double mouseY) {
		Mark mark = dragged;
		dragged = null;
		if (!dragMoved) {
			InkMaterial material = material();
			if (mark.isInk() && !mark.glow() && material != null && material.kind() == InkMaterial.Kind.GLOW) {
				// Clicked with glow ink in the second slot: the label or icon glows, as a glowing sign does.
				ClientMarks.unhide();
				sendMark(MarkEditPayload.Action.GLOW, mark.id(), mark.x(), mark.y(), mark.size(), "");
			} else if (mark.isLabel() && canWrite()) {
				startEditing(new LabelEdit(mark, mark.x(), mark.y(), mark.size(), mark.text()));
				ClientMarks.hide(shownMap(), mark.id());
			} else {
				ClientMarks.unhide();
			}
			return;
		}
		if (!overCanvas(mouseX, mouseY)) {
			sendMark(MarkEditPayload.Action.REMOVE, mark.id(), mark.x(), mark.y(), mark.size(), "");
			pendingHidden = mark.id();
			return;
		}
		int toX = mark.x() + dragX - pressX;
		int toY = mark.y() + dragY - pressY;
		int x = mark.isLabel() ? Labels.clampX(mark.text(), mark.size(), toX) : Mark.clampPixel(toX);
		int y = mark.isLabel() ? Labels.clampY(mark.size(), toY) : Mark.clampPixel(toY);
		sendMark(MarkEditPayload.Action.MOVE, mark.id(), x, y, mark.size(), "");
		pending = mark.at(x, y);
		pendingHidden = mark.id();
	}

	/**
	 * Starts typing a label. Keyboard text only reaches the game while text
	 * input is on, as it is for a focused text box, so it's turned on until the
	 * label is placed or dropped.
	 */
	private void startEditing(LabelEdit edit) {
		editing = edit;
		scrolled = 0;
		minecraft.textInputManager().startTextInput(this);
	}

	private void stopEditing() {
		editing = null;
		minecraft.textInputManager().stopTextInput(this);
	}

	private void commitLabel() {
		LabelEdit edit = editing;
		stopEditing();
		String text = Mark.clean(edit.text.toString());
		DyeColor ink = labelInk();
		if (text.isEmpty() || ink == null) {
			ClientMarks.unhide();
			return;
		}
		int cost = Mark.cost(text, edit.size);
		if (edit.old != null && edit.old.color() == ink) {
			cost = Math.max(0, cost - Mark.cost(edit.old.text(), edit.old.size()));
		}
		if (cost > 0 && !affordable(cost, material())) {
			deny();
			ClientMarks.unhide();
			return;
		}
		int x = edit.old != null ? edit.old.x() : Labels.clampX(text, edit.size, edit.x);
		int y = edit.old != null ? edit.old.y() : Labels.clampY(edit.size, edit.y);
		sendMark(edit.old == null ? MarkEditPayload.Action.WRITE : MarkEditPayload.Action.REWRITE, edit.markId, x, y, edit.size, text);
		pending = new Mark(edit.markId, Mark.Kind.LABEL, x, y, ink, text, edit.size, Optional.empty());
		pendingHidden = edit.markId;
	}

	private void cancelLabel() {
		stopEditing();
		ClientMarks.unhide();
	}

	private void sendMark(MarkEditPayload.Action action, int markId, int x, int y, int size, String text) {
		pendingSeq = DeskState.nextSeq();
		pendingMap = target();
		pendingUpdates = ClientMarks.updates(pendingMap);
		pendingTicks = PENDING_TICKS;
		ClientPlayNetworking.send(new MarkEditPayload(pendingSeq, target(), action, markId, x, y, size, text));
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
	}

	private void deny() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
	}
}
