package com.assilf.mapplus.client.desk;

import com.assilf.mapplus.client.marks.ClientMarks;
import com.assilf.mapplus.client.marks.Labels;
import com.assilf.mapplus.client.mixin.AbstractContainerScreenAccessor;
import com.assilf.mapplus.ink.Ink;
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
 */
public final class DeskWing {
	/** The vanilla window is this wide; the wing makes it {@link #WIDTH}. */
	public static final int VANILLA_WIDTH = 176;
	public static final int WIDTH = 314;
	private static final int HEIGHT = 166;

	private static final int CANVAS_X = 179;
	private static final int CANVAS_Y = 8;
	private static final int RACK_X = 179;
	private static final int RACK_Y = 141;
	private static final int BUTTON_WIDTH = 16;
	private static final int BUTTON_HEIGHT = 18;
	/** Borders are dashes this long, with gaps this long. */
	private static final int DASH = 3;
	private static final int GAP = 2;
	/** How long an edit is shown before the server's answer, at most. */
	private static final int PENDING_TICKS = 60;
	/** The vanilla button that swaps a slot with the off hand. */
	private static final int OFF_HAND_BUTTON = 40;

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

	private final CartographyTableScreen screen;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final MapRenderState mapRenderState = new MapRenderState();
	private final InkPreview preview = new InkPreview();
	private int ticks;

	/** The tool of the stroke being drawn, or null. */
	private InkTool stroke;
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
		return input.is(Items.MAP) && result.is(Items.FILLED_MAP) ? result.get(DataComponents.MAP_ID) : null;
	}

	private boolean hasCanvas() {
		ItemStack input = input();
		return input.is(Items.FILLED_MAP) || input.is(Items.MAP);
	}

	private boolean sealed() {
		return DeskState.get().sealed();
	}

	private boolean canDraw(InkTool tool) {
		return tool != null && tool.draws() && unlocked(tool) && hasCanvas() && material() != null && !sealed();
	}

	private boolean canWrite() {
		return unlocked(InkTool.LABEL) && hasCanvas() && labelInk() != null && !sealed();
	}

	/** Pins go on maps that exist: a filled map, or a blank canvas that has been drawn on. */
	private boolean canPin() {
		return unlocked(InkTool.PIN) && shownMap() != null && !sealed();
	}

	private MapItemSavedData mapData(MapId mapId) {
		return mapId == null || minecraft.level == null ? null : minecraft.level.getMapData(mapId);
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
			InkPreview.drawRuns(graphics, x, y, shape, InkPreview.previewColor(material));
		}

		InkTool tool = tool();
		if (sealed()) {
			graphics.item(new ItemStack(Items.HONEYCOMB), x + Ink.SIZE - 18, y + 2);
		} else if (canDraw(tool) && overCanvas(mouseX, mouseY) && stroke == null && tool != InkTool.FILL) {
			// The tool's tip under the cursor.
			BitSet cursor = new BitSet(Ink.PIXELS);
			Strokes.dab(cursor, pixelX(mouseX), pixelY(mouseY), tool.size());
			InkPreview.drawRuns(graphics, x, y, cursor, (InkPreview.previewColor(material) & 0x00FFFFFF) | 0x99000000);
		}
	}

	/** Labels being typed, dragged or on their way to the server, drawn on the map at the pose's origin. */
	private void extractEdits(GuiGraphicsExtractor graphics) {
		if (pending != null && pending.isLabel()) {
			Labels.draw(graphics, pending.text(), pending.x(), pending.y(), pending.size(), Labels.color(pending.color()));
		}
		if (dragged != null && dragMoved) {
			int x = Mark.clampPixel(dragged.x() + dragX - pressX);
			int y = Mark.clampPixel(dragged.y() + dragY - pressY);
			if (dragged.isLabel()) {
				Labels.draw(graphics, dragged.text(), x, y, dragged.size(), Labels.color(dragged.color()));
			} else {
				graphics.pose().pushMatrix();
				graphics.pose().translate(x - 3, y - 4);
				graphics.pose().scale(0.5F, 0.5F);
				graphics.item(icon(InkTool.PIN), 0, 0);
				graphics.pose().popMatrix();
			}
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

		if (overCanvas(mouseX, mouseY) && stroke == null && dragged == null && editing == null) {
			Component hint = canvasHint(pixelX(mouseX), pixelY(mouseY));
			if (hint != null) {
				graphics.setTooltipForNextFrame(minecraft.font, minecraft.font.split(hint, 170), mouseX, mouseY);
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
		if (material() == null) {
			return Component.translatable("mapplus.desk.hint.material");
		}
		return null;
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
			click(empty, InputConstants.MOUSE_BUTTON_LEFT, ContainerInput.PICKUP);
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
			click(empty, InputConstants.MOUSE_BUTTON_LEFT, ContainerInput.PICKUP);
		}
		int slot = inventorySlot(tool);
		if (slot >= 0) {
			click(slot, InputConstants.MOUSE_BUTTON_LEFT, ContainerInput.PICKUP);
			return;
		}
		// A tool held in the off hand comes out through an empty slot, as the swap key would move it.
		int empty = emptyInventorySlot();
		if (minecraft.player != null && tool.matches(minecraft.player.getOffhandItem()) && empty >= 0) {
			click(empty, OFF_HAND_BUTTON, ContainerInput.SWAP);
			click(empty, InputConstants.MOUSE_BUTTON_LEFT, ContainerInput.PICKUP);
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
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return true;
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
		if (editing != null) {
			// Clicking anywhere else finishes the label being typed.
			commitLabel();
			return true;
		}
		if (!overCanvas(mouseX, mouseY)) {
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
			case PEN, BRUSH -> {
				if (canDraw(tool)) {
					stroke = tool;
					lastX = x;
					lastY = y;
					penDab(x, y, x, y);
				}
			}
			case LINE, BOX, BORDER -> {
				if (canDraw(tool)) {
					stroke = tool;
					anchorX = x;
					anchorY = y;
					reshape(x, y);
				}
			}
			case FILL -> {
				if (canDraw(tool)) {
					fill(x, y);
				}
			}
			case LABEL -> {
				Mark hit = markAt(x, y, Mark.Kind.LABEL);
				if (sealed()) {
					deny();
				} else if (hit != null) {
					startDrag(hit, x, y);
				} else if (canWrite()) {
					editing = new LabelEdit(null, x, y, Mark.MEDIUM, "");
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
		}
		return true;
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
		// Far enough out to cover any drag, close enough that lines stay cheap.
		int x = Math.clamp(pixelX(mouseX), -Ink.SIZE, Ink.SIZE * 2);
		int y = Math.clamp(pixelY(mouseY), -Ink.SIZE, Ink.SIZE * 2);
		if (stroke == InkTool.PEN || stroke == InkTool.BRUSH) {
			penDab(lastX, lastY, x, y);
			lastX = x;
			lastY = y;
		} else {
			reshape(x, y);
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
		if (tool == InkTool.PEN || tool == InkTool.BRUSH) {
			flushPen();
		} else if (!shape.isEmpty()) {
			BitSet pixels = (BitSet) shape.clone();
			shape.clear();
			send(pixels, true);
		}
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (editing == null) {
			return false;
		}
		// Scrolling while typing sizes the label. Touchpads scroll in small steps; change once per whole step.
		if (Math.signum(amount) != Math.signum(scrolled)) {
			scrolled = 0;
		}
		scrolled += amount;
		int steps = (int) scrolled;
		if (steps != 0) {
			scrolled -= steps;
			int size = Mark.clampSize(editing.size + steps);
			// A label never grows wider than the map.
			if (Labels.width(editing.text.toString(), size) <= Labels.MAX_WIDTH) {
				editing.size = size;
			}
		}
		return true;
	}

	/** While a label is being typed, every key goes to it, so E and the number keys type letters. */
	public boolean keyPressed(KeyEvent event) {
		if (editing == null) {
			return false;
		}
		switch (event.key()) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> commitLabel();
			case InputConstants.KEY_ESCAPE -> cancelLabel();
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
		preview.acknowledge(DeskState.get().ack());
		if (stroke != null && !canDraw(stroke)) {
			stroke = null;
			shape.clear();
			penPixels.clear();
		}
		if (editing != null && (tool() != InkTool.LABEL || !canWrite())) {
			cancelLabel();
		}
		if (dragged != null && tool() != (dragged.isLabel() ? InkTool.LABEL : InkTool.PIN)) {
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
	}

	/** Sends what the pen drew, before the window tells the server it closed. */
	public void close() {
		flushPen();
		pressButton = -1;
		stroke = null;
		shape.clear();
		editing = null;
		dragged = null;
		ClientMarks.unhide();
	}

	// Strokes

	private void penDab(int x0, int y0, int x1, int y1) {
		BitSet pixels = new BitSet(Ink.PIXELS);
		Strokes.line(pixels, x0, y0, x1, y1, stroke.size());
		if (penSeq == 0) {
			penSeq = DeskState.nextSeq();
		}
		penPixels.or(pixels);
		InkMaterial material = material();
		if (material != null) {
			preview.add(pixels, penSeq, InkPreview.previewColor(material));
		}
	}

	private void flushPen() {
		if (penPixels.isEmpty()) {
			return;
		}
		ClientPlayNetworking.send(new InkStrokePayload(penSeq, target(), false, (BitSet) penPixels.clone()));
		penPixels.clear();
		penSeq = 0;
	}

	private void reshape(int x, int y) {
		shape.clear();
		switch (stroke) {
			case LINE -> Strokes.line(shape, anchorX, anchorY, x, y, 1);
			case BOX -> Strokes.box(shape, Math.min(anchorX, x), Math.min(anchorY, y), Math.max(anchorX, x), Math.max(anchorY, y), 1);
			case BORDER -> Strokes.dashed(shape, anchorX, anchorY, x, y, DASH, GAP);
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
		send(pixels, true);
	}

	/** Sends a finished stroke and shows it until the server has drawn it. */
	private void send(BitSet pixels, boolean whole) {
		InkMaterial material = material();
		if (material == null || pixels.isEmpty()) {
			return;
		}
		if (whole && !affordable(pixels.cardinality(), material)) {
			deny();
			return;
		}
		int seq = DeskState.nextSeq();
		preview.add(pixels, seq, InkPreview.previewColor(material));
		ClientPlayNetworking.send(new InkStrokePayload(seq, target(), whole, pixels));
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.2F));
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
			float halfWidth = mark.isLabel() ? Math.max(2, Labels.width(mark.text(), mark.size()) / 2) : 4;
			float halfHeight = mark.isLabel() ? Math.max(2, 4.5F * Mark.scale(mark.size())) : 4;
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
		if (mark.isLabel()) {
			ClientMarks.hide(shownMap(), mark.id());
		}
	}

	/** Dropping a mark moves it; dropping it off the map removes it; clicking a label rewrites it. */
	private void finishDrag(double mouseX, double mouseY) {
		Mark mark = dragged;
		dragged = null;
		if (!dragMoved) {
			if (mark.isLabel() && canWrite()) {
				editing = new LabelEdit(mark, mark.x(), mark.y(), mark.size(), mark.text());
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
		if (mark.isLabel()) {
			pending = mark.at(x, y);
			pendingHidden = mark.id();
		}
	}

	private void commitLabel() {
		LabelEdit edit = editing;
		editing = null;
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
		editing = null;
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
