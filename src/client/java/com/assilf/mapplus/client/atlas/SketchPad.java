package com.assilf.mapplus.client.atlas;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.atlas.Sketch;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.InkMaterial;
import com.assilf.mapplus.net.SketchPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Sketching in an open book and quill. With ink in the other hand, the book's
 * own quill draws on an empty page: a dye or ink sac in its color, a glow ink
 * sac makes ink glow, a water bottle washes it out. Shift lays a light coat;
 * the right button erases. Strokes go to the server as each ends, and show at
 * once, until the book comes back with them.
 */
public final class SketchPad {
	/** A light coat, as Shift gives. */
	private static final int LIGHT = 2;
	/** How long a stroke shows before the book comes back with it, in ticks. */
	private static final int PENDING_TICKS = 40;

	private final SketchBookScreen book;
	private final Minecraft minecraft = Minecraft.getInstance();

	private boolean drawing;
	private boolean erasing;
	private int strokePage;
	private int lastX;
	private int lastY;
	private final BitSet stroke = new BitSet(Sketch.PIXELS);
	private InkMaterial strokeMaterial;
	private int strokeStrength;

	/** Each page's sketch as drawn here, until the book in hand has caught up. */
	private final Map<Integer, byte[]> pending = new HashMap<>();
	private CustomData sentWith;
	private int pendingTicks;
	private int scratchTicks;

	public SketchPad(SketchBookScreen book) {
		this.book = book;
	}

	private ItemStack bookStack() {
		return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(book.mapplus$bookHand());
	}

	private InteractionHand inkHand() {
		return book.mapplus$bookHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	/** The ink in the other hand, or null. */
	private InkMaterial ink() {
		return minecraft.player == null ? null : InkMaterial.of(minecraft.player.getItemInHand(inkHand()));
	}

	private Atlas live() {
		return Atlas.read(bookStack()).orElse(Atlas.EMPTY);
	}

	/** A page's sketch as it shows now, or null for none. */
	public byte[] sketch(int page) {
		byte[] drawn = pending.get(page);
		if (drawn != null) {
			return drawn;
		}
		Sketch sketch = live().sketchAt(page);
		return sketch == null ? null : sketch.ink();
	}

	/** Whether a page shows a sketch, so it has no room for text. */
	public boolean isSketch(int page) {
		return sketch(page) != null;
	}

	/** Whether a page can be drawn on: no map, no text. */
	private boolean canSketch(int page) {
		return !book.mapplus$isMapPage(page) && !book.mapplus$hasText(page);
	}

	/** The point of the writing area under the mouse, or null if it's outside. */
	private int[] point(double mouseX, double mouseY) {
		int x = (int) Math.floor(mouseX) - book.mapplus$writingX();
		int y = (int) Math.floor(mouseY) - book.mapplus$writingY();
		return x < 0 || y < 0 || x >= Sketch.WIDTH || y >= Sketch.HEIGHT ? null : new int[] {x, y};
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int page = book.mapplus$currentPage();
		int[] point = point(mouseX, mouseY);
		if (point == null || !canSketch(page) || drawing) {
			return false;
		}
		InkMaterial material = ink();
		if (button == InputConstants.MOUSE_BUTTON_RIGHT && isSketch(page)) {
			erasing = true;
		} else if (button == InputConstants.MOUSE_BUTTON_LEFT && material != null) {
			erasing = false;
		} else {
			return false;
		}
		drawing = true;
		strokePage = page;
		strokeMaterial = material;
		strokeStrength = minecraft.hasShiftDown() ? LIGHT : Ink.FULL;
		stroke.clear();
		lastX = point[0];
		lastY = point[1];
		paint(point[0], point[1], point[0], point[1]);
		return true;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button) {
		if (!drawing) {
			return false;
		}
		int x = Math.clamp((int) Math.floor(mouseX) - book.mapplus$writingX(), 0, Sketch.WIDTH - 1);
		int y = Math.clamp((int) Math.floor(mouseY) - book.mapplus$writingY(), 0, Sketch.HEIGHT - 1);
		if (x != lastX || y != lastY) {
			paint(lastX, lastY, x, y);
			lastX = x;
			lastY = y;
		}
		return true;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (!drawing) {
			return false;
		}
		finish();
		return true;
	}

	/** Sends the stroke being drawn, as when the book closes mid-stroke. */
	public void finish() {
		if (!drawing) {
			return;
		}
		drawing = false;
		if (!stroke.isEmpty() && ClientPlayNetworking.canSend(SketchPayload.TYPE)) {
			ClientPlayNetworking.send(new SketchPayload(book.mapplus$bookHand() == InteractionHand.OFF_HAND, strokePage, strokeStrength, erasing,
					(BitSet) stroke.clone()));
			sentWith = bookStack().get(DataComponents.CUSTOM_DATA);
			pendingTicks = PENDING_TICKS;
		}
		stroke.clear();
	}

	/** Lays the stroke's ink along a line, here at once; the server pays for it and keeps it. */
	private void paint(int x0, int y0, int x1, int y1) {
		byte[] ink = pending.get(strokePage);
		if (ink == null) {
			byte[] shown = sketch(strokePage);
			ink = shown == null ? new byte[Sketch.PIXELS] : shown.clone();
			pending.put(strokePage, ink);
		}
		int dx = Math.abs(x1 - x0);
		int dy = -Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int error = dx + dy;
		while (true) {
			int i = x0 + y0 * Sketch.WIDTH;
			stroke.set(i);
			ink[i] = erasing ? Ink.NONE : strokeMaterial.apply(ink[i], strokeStrength);
			if (x0 == x1 && y0 == y1) {
				break;
			}
			int twice = 2 * error;
			if (twice >= dy) {
				error += dy;
				x0 += sx;
			}
			if (twice <= dx) {
				error += dx;
				y0 += sy;
			}
		}
		// The scratch of the quill on paper, now and then while it moves.
		if (scratchTicks <= 0 && minecraft.level != null) {
			scratchTicks = 4;
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
					1.1F + minecraft.level.getRandom().nextFloat() * 0.4F, 0.2F));
		}
	}

	public void tick() {
		if (scratchTicks > 0) {
			scratchTicks--;
		}
		if (pending.isEmpty() || drawing) {
			return;
		}
		// The book in hand has the strokes once it changes; if it never does, the server didn't take them.
		pendingTicks--;
		if (bookStack().get(DataComponents.CUSTOM_DATA) != sentWith || pendingTicks <= 0) {
			pending.clear();
		}
	}

	/** Draws the current page's sketch, and where the quill would draw. */
	public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int page = book.mapplus$currentPage();
		int x = book.mapplus$writingX();
		int y = book.mapplus$writingY();
		byte[] ink = sketch(page);
		if (ink != null) {
			SketchView.draw(graphics, ink, x, y);
		}
		InkMaterial material = ink();
		int[] point = point(mouseX, mouseY);
		if (!drawing && point != null && material != null && material.dye() != null && canSketch(page)) {
			// The quill's tip, in the ink it would draw with.
			byte tip = Ink.encode(material.dye(), false, minecraft.hasShiftDown() ? LIGHT : Ink.FULL);
			graphics.fill(x + point[0], y + point[1], x + point[0] + 1, y + point[1] + 1, SketchView.color(tip));
		}
	}
}
