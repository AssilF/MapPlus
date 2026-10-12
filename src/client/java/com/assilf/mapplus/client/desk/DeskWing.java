package com.assilf.mapplus.client.desk;

import com.assilf.mapplus.client.mixin.AbstractContainerScreenAccessor;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.InkMaterial;
import com.assilf.mapplus.net.InkStatePayload;
import com.assilf.mapplus.net.InkStrokePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/**
 * The drawing desk: a wing on the right of the vanilla cartography table
 * window, in the same style, holding the map at full size and the drawing
 * tools. Shown to players who play with the mod on both sides.
 */
public final class DeskWing {
	/** The vanilla window is this wide; the wing makes it {@link #WIDTH}. */
	public static final int VANILLA_WIDTH = 176;
	public static final int WIDTH = 314;
	private static final int HEIGHT = 166;

	private static final int CANVAS_X = 179;
	private static final int CANVAS_Y = 8;
	private static final int TOOL_Y = 141;
	private static final int TOOL_X = 178;
	private static final int BRUSH_X = 260;
	private static final int BUTTON_WIDTH = 16;
	private static final int BUTTON_HEIGHT = 18;

	private static final int FILL = 0xFFC6C6C6;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_INSIDE = 0xFF8B8B8B;
	private static final int BRUSH_COLOR = 0xFF373737;

	private static final Identifier TABLE_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/cartography_table.png");
	private static final Identifier MAP_BACKGROUND = Identifier.withDefaultNamespace("textures/map/map_background.png");
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("container/stonecutter/recipe");
	private static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");
	private static final Identifier BUTTON_SELECTED = Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");

	/** Kept between tables, like vanilla keeps the last stonecutter recipe page. */
	private static DeskTool tool = DeskTool.PEN;
	private static int brush = 1;

	private final CartographyTableScreen screen;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final MapRenderState mapRenderState = new MapRenderState();
	private final InkPreview preview = new InkPreview();

	private boolean drawing;
	private int anchorX;
	private int anchorY;
	private int lastX;
	private int lastY;
	/** Pen pixels not sent yet, and the stroke number they'll be sent under. */
	private final BitSet penPixels = new BitSet(Ink.PIXELS);
	private int penSeq;
	/** The line or box being dragged out. */
	private final BitSet shape = new BitSet(Ink.PIXELS);

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

	/** The map shown on the canvas, or null for a blank canvas or no map. */
	private MapId shownMap() {
		ItemStack input = input();
		if (input.is(Items.FILLED_MAP)) {
			return input.get(DataComponents.MAP_ID);
		}
		int canvas = DeskState.get().canvasId();
		return input.is(Items.MAP) && canvas >= 0 ? new MapId(canvas) : null;
	}

	private boolean hasCanvas() {
		ItemStack input = input();
		return input.is(Items.FILLED_MAP) || input.is(Items.MAP);
	}

	private boolean canDraw() {
		return hasCanvas() && material() != null && !DeskState.get().sealed();
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

	private int pixelX(double mouseX) {
		return Math.clamp((int) Math.floor(mouseX - left() - CANVAS_X), 0, Ink.SIZE - 1);
	}

	private int pixelY(double mouseY) {
		return Math.clamp((int) Math.floor(mouseY - top() - CANVAS_Y), 0, Ink.SIZE - 1);
	}

	private int toolX(int index) {
		return TOOL_X + index * BUTTON_WIDTH;
	}

	private int brushX(int size) {
		return BRUSH_X + (size - 1) * BUTTON_WIDTH;
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

		DeskTool[] tools = DeskTool.values();
		for (int i = 0; i < tools.length; i++) {
			int x = left + toolX(i);
			int y = top + TOOL_Y;
			extractButton(graphics, x, y, tools[i] == tool, over(mouseX, mouseY, toolX(i), TOOL_Y, BUTTON_WIDTH, BUTTON_HEIGHT));
			graphics.item(tools[i].icon(), x, y + 1);
		}
		for (int size = 1; size <= 3; size++) {
			int x = left + brushX(size);
			int y = top + TOOL_Y;
			extractButton(graphics, x, y, size == brush, over(mouseX, mouseY, brushX(size), TOOL_Y, BUTTON_WIDTH, BUTTON_HEIGHT));
			int dot = size * 2;
			int dotX = x + (BUTTON_WIDTH - dot) / 2;
			int dotY = y + (BUTTON_HEIGHT - dot) / 2;
			graphics.fill(dotX, dotY, dotX + dot, dotY + dot, BRUSH_COLOR);
		}
	}

	private static void extractButton(GuiGraphicsExtractor graphics, int x, int y, boolean selected, boolean hovered) {
		Identifier sprite = selected ? BUTTON_SELECTED : hovered ? BUTTON_HIGHLIGHTED : BUTTON;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
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

		// The paper a held map is drawn on, then the map's own texture, as in item frames.
		graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_BACKGROUND, x, y, 7, 7, Ink.SIZE, Ink.SIZE, Ink.SIZE, Ink.SIZE, 142, 142);
		MapId mapId = shownMap();
		MapItemSavedData data = mapData(mapId);
		if (data != null) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			minecraft.getMapRenderer().extractRenderState(mapId, data, mapRenderState);
			graphics.map(mapRenderState);
			graphics.pose().popMatrix();
		}

		preview.extract(graphics, x, y);
		InkMaterial material = material();
		if (material != null && !shape.isEmpty()) {
			InkPreview.drawRuns(graphics, x, y, shape, InkPreview.previewColor(material));
		}

		if (DeskState.get().sealed()) {
			graphics.item(new ItemStack(Items.HONEYCOMB), x + Ink.SIZE - 18, y + 2);
		} else if (canDraw() && overCanvas(mouseX, mouseY) && !drawing && tool != DeskTool.FILL) {
			// The brush outline under the cursor.
			BitSet cursor = new BitSet(Ink.PIXELS);
			Strokes.dab(cursor, pixelX(mouseX), pixelY(mouseY), brush);
			InkPreview.drawRuns(graphics, x, y, cursor, (InkPreview.previewColor(material) & 0x00FFFFFF) | 0x99000000);
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
		}

		if (overCanvas(mouseX, mouseY) && !drawing) {
			Component hint = canvasHint();
			if (hint != null) {
				graphics.setTooltipForNextFrame(minecraft.font, hint, mouseX, mouseY);
			}
		}
		DeskTool[] tools = DeskTool.values();
		for (int i = 0; i < tools.length; i++) {
			if (over(mouseX, mouseY, toolX(i), TOOL_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
				graphics.setTooltipForNextFrame(minecraft.font, List.of(tools[i].title(), tools[i].description().copy().withStyle(ChatFormatting.GRAY)), Optional.empty(), mouseX, mouseY);
			}
		}
		for (int size = 1; size <= 3; size++) {
			if (over(mouseX, mouseY, brushX(size), TOOL_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
				graphics.setTooltipForNextFrame(minecraft.font, Component.translatable("mapplus.desk.brush", size), mouseX, mouseY);
			}
		}
	}

	private Component canvasHint() {
		if (!hasCanvas()) {
			return Component.translatable("mapplus.desk.hint.map");
		}
		if (DeskState.get().sealed()) {
			return Component.translatable("mapplus.desk.hint.sealed");
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

	// Input

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!overWing(mouseX, mouseY)) {
			return false;
		}
		if (button != 0) {
			return true;
		}
		DeskTool[] tools = DeskTool.values();
		for (int i = 0; i < tools.length; i++) {
			if (over(mouseX, mouseY, toolX(i), TOOL_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
				tool = tools[i];
				click();
				return true;
			}
		}
		for (int size = 1; size <= 3; size++) {
			if (over(mouseX, mouseY, brushX(size), TOOL_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
				brush = size;
				click();
				return true;
			}
		}
		if (overCanvas(mouseX, mouseY) && canDraw()) {
			int x = pixelX(mouseX);
			int y = pixelY(mouseY);
			switch (tool) {
				case PEN -> {
					drawing = true;
					lastX = x;
					lastY = y;
					penDab(x, y, x, y);
				}
				case LINE, BOX -> {
					drawing = true;
					anchorX = x;
					anchorY = y;
					reshape(x, y);
				}
				case FILL -> fill(x, y);
			}
		}
		return true;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button) {
		if (!drawing) {
			return false;
		}
		int x = pixelX(mouseX);
		int y = pixelY(mouseY);
		if (tool == DeskTool.PEN) {
			penDab(lastX, lastY, x, y);
			lastX = x;
			lastY = y;
		} else {
			reshape(x, y);
		}
		return true;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (!drawing) {
			return false;
		}
		drawing = false;
		if (tool == DeskTool.PEN) {
			flushPen();
		} else if (!shape.isEmpty()) {
			BitSet pixels = (BitSet) shape.clone();
			shape.clear();
			send(pixels, true);
		}
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!overCanvas(mouseX, mouseY) || amount == 0) {
			return false;
		}
		brush = Math.clamp(brush + (amount > 0 ? 1 : -1), 1, 3);
		return true;
	}

	public void tick() {
		// Pen strokes go out as they're drawn, a tick's worth at a time.
		flushPen();
		preview.acknowledge(DeskState.get().ack());
		if (drawing && !canDraw()) {
			drawing = false;
			shape.clear();
			penPixels.clear();
		}
	}

	public void acknowledge(int ack) {
		preview.acknowledge(ack);
	}

	/** Sends what the pen drew before the window closed. */
	public void close() {
		flushPen();
		drawing = false;
		shape.clear();
	}

	private void penDab(int x0, int y0, int x1, int y1) {
		BitSet pixels = new BitSet(Ink.PIXELS);
		Strokes.line(pixels, x0, y0, x1, y1, brush);
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
		ClientPlayNetworking.send(new InkStrokePayload(penSeq, strokeTarget(), false, (BitSet) penPixels.clone()));
		penPixels.clear();
		penSeq = 0;
	}

	private void reshape(int x, int y) {
		shape.clear();
		if (tool == DeskTool.LINE) {
			Strokes.line(shape, anchorX, anchorY, x, y, brush);
		} else {
			Strokes.box(shape, Math.min(anchorX, x), Math.min(anchorY, y), Math.max(anchorX, x), Math.max(anchorY, y), brush);
		}
	}

	private void fill(int x, int y) {
		MapItemSavedData data = mapData(shownMap());
		byte[] colors = data == null ? new byte[Ink.PIXELS] : data.colors;
		BitSet pixels = new BitSet(Ink.PIXELS);
		Strokes.fill(pixels, colors, x, y);
		send(pixels, true);
	}

	/** Sends a finished stroke and shows it until the server has drawn it. */
	private void send(BitSet pixels, boolean whole) {
		InkMaterial material = material();
		if (material == null || pixels.isEmpty()) {
			return;
		}
		if (whole && !affordable(pixels.cardinality(), material)) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
			return;
		}
		int seq = DeskState.nextSeq();
		preview.add(pixels, seq, InkPreview.previewColor(material));
		ClientPlayNetworking.send(new InkStrokePayload(seq, strokeTarget(), whole, pixels));
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.2F));
	}

	/** A rough check before sending, so a shape that can't be paid for doesn't flash on the canvas. */
	private boolean affordable(int pixels, InkMaterial material) {
		InkStatePayload state = DeskState.get();
		if (state.free()) {
			return true;
		}
		ItemStack stack = menu().getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem();
		return state.charge() + (long) stack.getCount() * material.perItem() >= pixels;
	}

	private int strokeTarget() {
		ItemStack input = input();
		if (input.is(Items.FILLED_MAP)) {
			MapId mapId = input.get(DataComponents.MAP_ID);
			return mapId == null ? InkStrokePayload.NEW_CANVAS : mapId.id();
		}
		int canvas = DeskState.get().canvasId();
		return canvas >= 0 ? canvas : InkStrokePayload.NEW_CANVAS;
	}

	private void click() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}
