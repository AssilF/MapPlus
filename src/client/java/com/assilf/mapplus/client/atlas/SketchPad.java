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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Sketching in an open book and quill. With ink in the other hand, the book's
 * own quill draws on a page, under its writing if it has any: a dye or ink sac
 * in its color, a glow ink sac makes ink glow, a water bottle washes it out.
 * Shift lays a light coat; the wheel sizes the pen; the right button erases
 * with a broad eraser, and Shift with the right button wipes the page clean.
 * Without ink in hand, a click places the text cursor as usual. Strokes go to
 * the server as each ends; what's drawn here stays shown while the book is
 * open, whenever the book in hand catches up.
 */
public final class SketchPad {
	/** A light coat, as Shift gives. */
	private static final int LIGHT = 2;
	/** The pen's sizes, in points across, chosen with the wheel. */
	private static final int SMALLEST = 1;
	private static final int LARGEST = 4;
	/** How far the eraser reaches around its point. */
	private static final int ERASER_RADIUS = 6;
	/** How often the quill is heard while it moves, in ticks. */
	private static final int SCRATCH_TICKS = 7;

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

	/** Each page's sketch as drawn here while the book is open; the book in hand may be a little behind. */
	private final Map<Integer, byte[]> drawn = new HashMap<>();
	private int scratchTicks;
	/** The pen's size, kept from book to book. */
	private static int size = SMALLEST;
	private double scrolled;

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
		byte[] here = drawn.get(page);
		if (here != null) {
			return isBlank(here) ? null : here;
		}
		Sketch sketch = live().sketchAt(page);
		return sketch == null ? null : sketch.ink();
	}

	private static boolean isBlank(byte[] ink) {
		for (byte pixel : ink) {
			if (Ink.isPresent(pixel)) {
				return false;
			}
		}
		return true;
	}

	/** Whether a page shows a sketch. */
	public boolean isSketch(int page) {
		return sketch(page) != null;
	}

	/** Whether a page can be drawn on: any page but a map's. */
	private boolean canSketch(int page) {
		return !book.mapplus$isMapPage(page);
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
		if (button == InputConstants.MOUSE_BUTTON_RIGHT && isSketch(page) && minecraft.hasShiftDown()) {
			// Shift and the right button wipe the page clean.
			wipe(page);
			return true;
		} else if (button == InputConstants.MOUSE_BUTTON_RIGHT && isSketch(page)) {
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
		}
		stroke.clear();
	}

	/** Wipes a page's sketch away, all at once, with a sound of the page brushed off. */
	private void wipe(int page) {
		BitSet all = new BitSet(Sketch.PIXELS);
		all.set(0, Sketch.PIXELS);
		drawn.put(page, new byte[Sketch.PIXELS]);
		if (ClientPlayNetworking.canSend(SketchPayload.TYPE)) {
			ClientPlayNetworking.send(new SketchPayload(book.mapplus$bookHand() == InteractionHand.OFF_HAND, page, Ink.FULL, true, all));
		}
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.7F));
	}

	/** The wheel over the page, with ink in hand, sizes the pen. */
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (point(mouseX, mouseY) == null || ink() == null || !canSketch(book.mapplus$currentPage())) {
			return false;
		}
		if (Math.signum(amount) != Math.signum(scrolled)) {
			scrolled = 0;
		}
		scrolled += amount;
		int steps = (int) scrolled;
		if (steps != 0) {
			scrolled -= steps;
			size = Math.clamp(size + steps, SMALLEST, LARGEST);
		}
		return true;
	}

	/** Lays the stroke's ink along a line, here at once; the server pays for it and keeps it. */
	private void paint(int x0, int y0, int x1, int y1) {
		byte[] ink = drawn.get(strokePage);
		if (ink == null) {
			byte[] shown = sketch(strokePage);
			ink = shown == null ? new byte[Sketch.PIXELS] : shown.clone();
			drawn.put(strokePage, ink);
		}
		int dx = Math.abs(x1 - x0);
		int dy = -Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int error = dx + dy;
		while (true) {
			dab(ink, x0, y0);
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
		// The quill on paper, softly, now and then while it moves.
		if (scratchTicks <= 0 && minecraft.level != null) {
			scratchTicks = SCRATCH_TICKS;
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN,
					1.6F + minecraft.level.getRandom().nextFloat() * 0.3F, 0.12F));
		}
	}

	/** Inks the pen's square at a point, or clears the eraser's circle around it. */
	private void dab(byte[] ink, int x, int y) {
		int reach = erasing ? ERASER_RADIUS : (size - 1) / 2;
		int to = erasing ? ERASER_RADIUS : size / 2;
		for (int dy = -reach; dy <= to; dy++) {
			for (int dx = -reach; dx <= to; dx++) {
				if (erasing && dx * dx + dy * dy > ERASER_RADIUS * ERASER_RADIUS) {
					continue;
				}
				int px = x + dx;
				int py = y + dy;
				if (px < 0 || py < 0 || px >= Sketch.WIDTH || py >= Sketch.HEIGHT) {
					continue;
				}
				int i = px + py * Sketch.WIDTH;
				stroke.set(i);
				ink[i] = erasing ? Ink.NONE : strokeMaterial.apply(ink[i], strokeStrength);
			}
		}
	}

	public void tick() {
		if (scratchTicks > 0) {
			scratchTicks--;
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
			// The quill's tip, as big as it draws, in the ink it would draw with.
			byte tip = Ink.encode(material.dye(), false, minecraft.hasShiftDown() ? LIGHT : Ink.FULL);
			int from = (size - 1) / 2;
			graphics.fill(x + point[0] - from, y + point[1] - from, x + point[0] - from + size, y + point[1] - from + size, SketchView.color(tip));
		}
	}
}
