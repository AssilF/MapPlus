package com.assilf.mapplus.client.ink;

import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.InkItems;
import com.assilf.mapplus.ink.InkSupply;
import com.assilf.mapplus.net.InkSealPayload;
import com.assilf.mapplus.net.InkStatePayload;
import com.assilf.mapplus.net.InkStrokePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Drawing on a map with ink, opened from the cartography table.
 */
public class InkScreen extends Screen {
	private static final int PAPER = 0xFFE3D7B5;
	private static final int SWATCH = 14;
	private static final int PANEL = 92;

	private enum Tool {
		PEN("pen"), LINE("line"), BOX("box"), FILLED_BOX("filled_box"), BUCKET("bucket"), ERASER("eraser");

		final String key;

		Tool(String key) {
			this.key = key;
		}
	}

	private final Screen parent;
	private final int mapId;

	private boolean loaded;
	private boolean sealed;
	private byte[] colors = new byte[Ink.PIXELS];
	private byte[] ink = new byte[Ink.PIXELS];
	private InkSupply supply = InkSupply.EMPTY;

	private Tool tool = Tool.PEN;
	private DyeColor dye = DyeColor.BLACK;
	private boolean glow;
	private int brush = 1;

	private boolean drawing;
	private boolean erasing;
	private int startX;
	private int startY;
	private int lastX;
	private int lastY;
	private byte[] pending = new byte[InkStrokePayload.MASK_BYTES];

	private int zoom;
	private int canvasX;
	private int canvasY;
	private int paletteX;
	private int paletteY;

	private final List<Button> toolButtons = new ArrayList<>();
	private Button brushButton;
	private Button glowButton;
	private Button sealButton;

	public InkScreen(Screen parent, int mapId) {
		super(Component.translatable("mapplus.ink.title"));
		this.parent = parent;
		this.mapId = mapId;
	}

	public int mapId() {
		return mapId;
	}

	public void accept(InkStatePayload state) {
		if (!state.open()) {
			onClose();
			return;
		}
		loaded = true;
		sealed = state.sealed();
		colors = state.colors();
		ink = state.ink();
		supply = state.supply();
		updateButtons();
	}

	@Override
	protected void init() {
		int size = Math.max(1, Math.min((height - 48) / Ink.SIZE, (width - 2 * PANEL - 16) / Ink.SIZE));
		zoom = size;
		int canvasSize = Ink.SIZE * zoom;
		canvasX = (width - canvasSize) / 2;
		canvasY = Math.max(24, (height - canvasSize) / 2);
		paletteX = canvasX + canvasSize + 8;
		paletteY = canvasY;

		int toolX = Math.max(4, canvasX - PANEL);
		int y = canvasY;
		toolButtons.clear();
		for (Tool each : Tool.values()) {
			Button button = Button.builder(Component.translatable("mapplus.ink.tool." + each.key), b -> {
				tool = each;
				updateButtons();
			}).bounds(toolX, y, PANEL - 8, 20).build();
			toolButtons.add(addRenderableWidget(button));
			y += 22;
		}
		y += 6;
		brushButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			brush = brush % 3 + 1;
			updateButtons();
		}).bounds(toolX, y, PANEL - 8, 20).build());
		y += 22;
		glowButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			glow = !glow;
			updateButtons();
		}).bounds(toolX, y, PANEL - 8, 20).build());
		y += 22;
		sealButton = addRenderableWidget(Button.builder(Component.empty(), b ->
				ClientPlayNetworking.send(new InkSealPayload(mapId, !sealed))
		).bounds(toolX, y, PANEL - 8, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(width / 2 - 50, Math.min(height - 22, canvasY + canvasSize + 4), 100, 20).build());
		updateButtons();
	}

	private void updateButtons() {
		for (int i = 0; i < toolButtons.size(); i++) {
			toolButtons.get(i).active = loaded && !sealed && Tool.values()[i] != tool;
		}
		if (brushButton == null) {
			return;
		}
		brushButton.setMessage(Component.translatable("mapplus.ink.brush", brush));
		brushButton.active = loaded && !sealed;
		glowButton.setMessage(Component.translatable(glow ? "mapplus.ink.glow.on" : "mapplus.ink.glow.off"));
		glowButton.active = loaded && !sealed;
		sealButton.setMessage(Component.translatable(sealed ? "mapplus.ink.unseal" : "mapplus.ink.seal"));
		sealButton.active = loaded && (creative() || (sealed
				? count(stack -> stack.is(ItemTags.AXES)) > 0
				: count(stack -> stack.is(Items.HONEYCOMB)) > 0));
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// Rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(font, title, width / 2 - font.width(title) / 2, Math.max(4, canvasY - 14), 0xFFFFFFFF, true);

		int canvasSize = Ink.SIZE * zoom;
		graphics.fill(canvasX - 2, canvasY - 2, canvasX + canvasSize + 2, canvasY + canvasSize + 2, 0xFF5A4A35);
		if (!loaded) {
			graphics.fill(canvasX, canvasY, canvasX + canvasSize, canvasY + canvasSize, PAPER);
			Component loading = Component.translatable("mapplus.ink.loading");
			graphics.text(font, loading, canvasX + (canvasSize - font.width(loading)) / 2, canvasY + canvasSize / 2, 0xFF3A3020, false);
			return;
		}

		for (int y = 0; y < Ink.SIZE; y++) {
			int runStart = 0;
			int runColor = pixelColor(0, y);
			for (int x = 1; x <= Ink.SIZE; x++) {
				int color = x < Ink.SIZE ? pixelColor(x, y) : 0;
				if (x == Ink.SIZE || color != runColor) {
					graphics.fill(canvasX + runStart * zoom, canvasY + y * zoom, canvasX + x * zoom, canvasY + (y + 1) * zoom, runColor);
					runStart = x;
					runColor = color;
				}
			}
		}

		int hx = pixelAt(mouseX, canvasX);
		int hy = pixelAt(mouseY, canvasY);
		if (!sealed && inCanvas(hx, hy)) {
			int half = (brush - 1) / 2;
			int x0 = canvasX + (hx - half) * zoom;
			int y0 = canvasY + (hy - half) * zoom;
			graphics.outline(x0, y0, brush * zoom, brush * zoom, 0xAAFFFFFF);
		}

		renderPalette(graphics);
		renderSupply(graphics);
	}

	private void renderPalette(GuiGraphicsExtractor graphics) {
		for (DyeColor each : DyeColor.values()) {
			int sx = paletteX + (each.getId() % 4) * (SWATCH + 4);
			int sy = paletteY + (each.getId() / 4) * (SWATCH + 4);
			graphics.fill(sx, sy, sx + SWATCH, sy + SWATCH, inkColor(Ink.encode(each, false)));
			if (each == dye) {
				graphics.outline(sx - 2, sy - 2, SWATCH + 4, SWATCH + 4, 0xFFFFFFFF);
			} else if (available(each) <= 0 && !creative()) {
				graphics.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0x88000000);
			}
		}
	}

	private void renderSupply(GuiGraphicsExtractor graphics) {
		int x = paletteX;
		int y = paletteY + 4 * (SWATCH + 4) + 6;
		int color = 0xFFFFFFFF;
		Component dyeName = Component.translatable("color.minecraft." + dye.getName());
		graphics.text(font, dyeName, x, y, color, true);
		y += 11;
		graphics.text(font, Component.translatable("mapplus.ink.supply", supply.dye(dye), count(stack -> InkItems.isInkFor(stack, dye))), x, y, 0xFFDDDDDD, true);
		y += 14;
		graphics.text(font, Component.translatable("mapplus.ink.glow_supply", supply.glow(), count(InkItems::isGlowInk)), x, y, 0xFFB8F0E0, true);
		y += 11;
		graphics.text(font, Component.translatable("mapplus.ink.water_supply", supply.water(), count(InkItems::isWaterBottle)), x, y, 0xFFA0C8FF, true);
		y += 14;
		if (sealed) {
			graphics.text(font, Component.translatable("mapplus.ink.sealed"), x, y, 0xFFF0C040, true);
		} else if (creative() && MapPlusConfig.get().creativeInkIsFree) {
			graphics.text(font, Component.translatable("mapplus.ink.free"), x, y, 0xFF90E090, true);
		}
	}

	private int pixelColor(int x, int y) {
		int index = x + y * Ink.SIZE;
		if (InkStrokePayload.isSet(pending, index)) {
			return erasing || tool == Tool.ERASER ? baseColor(index) : inkColor(Ink.encode(dye, glow));
		}
		byte pixel = ink[index];
		return Ink.isPresent(pixel) ? inkColor(pixel) : baseColor(index);
	}

	private int baseColor(int index) {
		int packed = colors[index] & 0xFF;
		if (packed < 4) {
			return PAPER;
		}
		return 0xFF000000 | MapColor.getColorFromPackedId(packed);
	}

	private static int inkColor(byte pixel) {
		return 0xFF000000 | MapColor.getColorFromPackedId(Ink.toMapColor(pixel) & 0xFF);
	}

	// Input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int px = pixelAt(event.x(), canvasX);
		int py = pixelAt(event.y(), canvasY);
		if (loaded && !sealed && inCanvas(px, py) && (event.button() == 0 || event.button() == 1)) {
			drawing = true;
			erasing = event.button() == 1;
			pending = new byte[InkStrokePayload.MASK_BYTES];
			startX = lastX = px;
			startY = lastY = py;
			if (erasing || tool == Tool.PEN || tool == Tool.ERASER) {
				stamp(px, py);
			} else if (tool == Tool.BUCKET) {
				flood(px, py);
				commit();
			} else {
				shape(px, py);
			}
			return true;
		}
		if (loaded && event.button() == 0) {
			for (DyeColor each : DyeColor.values()) {
				int sx = paletteX + (each.getId() % 4) * (SWATCH + 4);
				int sy = paletteY + (each.getId() / 4) * (SWATCH + 4);
				if (event.x() >= sx && event.x() < sx + SWATCH && event.y() >= sy && event.y() < sy + SWATCH) {
					dye = each;
					if (tool == Tool.ERASER) {
						tool = Tool.PEN;
					}
					updateButtons();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (!drawing) {
			return super.mouseDragged(event, dragX, dragY);
		}
		int px = clamp(pixelAt(event.x(), canvasX));
		int py = clamp(pixelAt(event.y(), canvasY));
		if (erasing || tool == Tool.PEN || tool == Tool.ERASER) {
			line(lastX, lastY, px, py, true);
		} else if (tool != Tool.BUCKET) {
			pending = new byte[InkStrokePayload.MASK_BYTES];
			shape(px, py);
		}
		lastX = px;
		lastY = py;
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (drawing) {
			commit();
			return true;
		}
		return super.mouseReleased(event);
	}

	private void commit() {
		boolean erase = erasing || tool == Tool.ERASER;
		boolean any = false;
		for (byte b : pending) {
			if (b != 0) {
				any = true;
				break;
			}
		}
		if (any) {
			ClientPlayNetworking.send(new InkStrokePayload(mapId, erase ? InkStrokePayload.ERASE : dye.getId(), glow, pending));
			byte target = erase ? Ink.NONE : Ink.encode(dye, glow);
			byte[] preview = ink.clone();
			for (int i = 0; i < Ink.PIXELS; i++) {
				if (InkStrokePayload.isSet(pending, i)) {
					preview[i] = target;
				}
			}
			ink = preview;
		}
		pending = new byte[InkStrokePayload.MASK_BYTES];
		drawing = false;
		erasing = false;
	}

	private void shape(int px, int py) {
		switch (tool) {
			case LINE -> line(startX, startY, px, py, true);
			case BOX -> {
				line(startX, startY, px, startY, false);
				line(px, startY, px, py, false);
				line(px, py, startX, py, false);
				line(startX, py, startX, startY, false);
			}
			case FILLED_BOX -> {
				for (int y = Math.min(startY, py); y <= Math.max(startY, py); y++) {
					for (int x = Math.min(startX, px); x <= Math.max(startX, px); x++) {
						mark(x, y);
					}
				}
			}
			default -> {
			}
		}
	}

	private void line(int x0, int y0, int x1, int y1, boolean withBrush) {
		int dx = Math.abs(x1 - x0);
		int dy = -Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int err = dx + dy;
		while (true) {
			if (withBrush) {
				stamp(x0, y0);
			} else {
				mark(x0, y0);
			}
			if (x0 == x1 && y0 == y1) {
				return;
			}
			int e2 = 2 * err;
			if (e2 >= dy) {
				err += dy;
				x0 += sx;
			}
			if (e2 <= dx) {
				err += dx;
				y0 += sy;
			}
		}
	}

	private void stamp(int px, int py) {
		int half = (brush - 1) / 2;
		for (int y = py - half; y < py - half + brush; y++) {
			for (int x = px - half; x < px - half + brush; x++) {
				mark(x, y);
			}
		}
	}

	private void mark(int x, int y) {
		if (inCanvas(x, y)) {
			InkStrokePayload.set(pending, x + y * Ink.SIZE);
		}
	}

	private void flood(int px, int py) {
		int target = displayed(px + py * Ink.SIZE);
		boolean[] seen = new boolean[Ink.PIXELS];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		queue.add(px + py * Ink.SIZE);
		while (!queue.isEmpty()) {
			int index = queue.poll();
			if (seen[index] || displayed(index) != target) {
				continue;
			}
			seen[index] = true;
			InkStrokePayload.set(pending, index);
			int x = index % Ink.SIZE;
			int y = index / Ink.SIZE;
			if (x > 0) queue.add(index - 1);
			if (x < Ink.SIZE - 1) queue.add(index + 1);
			if (y > 0) queue.add(index - Ink.SIZE);
			if (y < Ink.SIZE - 1) queue.add(index + Ink.SIZE);
		}
	}

	private int displayed(int index) {
		byte pixel = ink[index];
		return Ink.isPresent(pixel) ? inkColor(pixel) : baseColor(index);
	}

	private int pixelAt(double mouse, int origin) {
		return (int) Math.floor((mouse - origin) / zoom);
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(Ink.SIZE - 1, value));
	}

	private static boolean inCanvas(int x, int y) {
		return x >= 0 && y >= 0 && x < Ink.SIZE && y < Ink.SIZE;
	}

	// Supplies

	private boolean creative() {
		Player player = minecraft.player;
		return player != null && player.isCreative();
	}

	private int available(DyeColor each) {
		return supply.dye(each) + count(stack -> InkItems.isInkFor(stack, each));
	}

	private int count(Predicate<ItemStack> matches) {
		Player player = minecraft.player;
		if (player == null) {
			return 0;
		}
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (matches.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
