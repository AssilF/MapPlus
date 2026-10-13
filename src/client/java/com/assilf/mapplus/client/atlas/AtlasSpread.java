package com.assilf.mapplus.client.atlas;

import com.assilf.mapplus.atlas.Atlas;
import com.google.common.collect.MapMaker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Atlases in the vanilla book screens. A map page is shown facing the other
 * page of its spread: the book's page is drawn as usual, and the map page is
 * drawn beside it, on the left or the right as it sits in the book. Turning
 * pages steps over map pages, since they're always shown with their notes.
 */
public final class AtlasSpread {
	/** The vanilla page texture is 192 wide; the page itself spans 20 to 165, its clean paper 30 to 152. */
	private static final int PAGE_TEXTURE = 192;
	/** How far the facing page sits from the vanilla page. */
	private static final int FACING_OFFSET = 148;
	/** A mirrored page's paper starts this much further right. */
	private static final int MIRROR_OFFSET = 4;
	private static final int PASTED_SIZE = 120;
	private static final int PASTED_TOP = 26;
	private static final int TITLE_TOP = 16;
	private static final int TEXT_TOP = 32;
	private static final int TEXT_WIDTH = 114;
	private static final int INK = 0xFF000000;

	private static final Identifier MAP_BACKGROUND = Identifier.withDefaultNamespace("textures/map/map_background.png");

	/** The item each atlas view in a book screen was made from. */
	private static final Map<BookViewScreen.BookAccess, ItemStack> SOURCES = new MapMaker().weakKeys().makeMap();

	private AtlasSpread() {
	}

	public static void remember(BookViewScreen.BookAccess access, ItemStack stack) {
		SOURCES.put(access, stack);
	}

	public static ItemStack source(BookViewScreen.BookAccess access) {
		return access == null ? ItemStack.EMPTY : SOURCES.getOrDefault(access, ItemStack.EMPTY);
	}

	/**
	 * What the screen shows around the current page.
	 *
	 * @param here        the map on the current page, if it is a map page
	 * @param facing      the other page of the spread, or -1 if it isn't shown
	 * @param facingRight whether the facing page is on the right
	 */
	public record Layout(Atlas.Plate here, int facing, boolean facingRight) {
		/** How far the vanilla page moves so the whole spread is centered. */
		public int shift() {
			if (facing < 0) {
				return 0;
			}
			return facingRight ? -75 : 73;
		}

		/** Whether the vanilla page is drawn mirrored, as the left page of the spread. */
		public boolean mirrored() {
			return facing >= 0 && facingRight;
		}
	}

	/** The spread for a page, or null if it has no map and the screen stays vanilla. */
	public static Layout layout(Atlas atlas, int page, int pageCount) {
		if (atlas == null) {
			return null;
		}
		int partner = Atlas.partner(page);
		Atlas.Plate here = atlas.plateAt(page);
		Atlas.Plate there = partner < pageCount ? atlas.plateAt(partner) : null;
		if (here == null && there == null) {
			return null;
		}
		return new Layout(here, partner < pageCount ? partner : -1, partner > page);
	}

	/**
	 * Whether turning pages stops on a page. Map pages are shown with their
	 * partner instead, unless they have none, or face another map.
	 */
	public static boolean isStop(Atlas atlas, int page, int pageCount) {
		if (atlas == null || !atlas.isMapPage(page)) {
			return true;
		}
		int partner = Atlas.partner(page);
		return partner >= pageCount || (atlas.isMapPage(partner) && page < partner);
	}

	public static int nextStop(Atlas atlas, int page, int pageCount) {
		for (int next = page + 1; next < pageCount; next++) {
			if (isStop(atlas, next, pageCount)) {
				return next;
			}
		}
		return -1;
	}

	public static int previousStop(Atlas atlas, int page, int pageCount) {
		for (int previous = page - 1; previous >= 0; previous--) {
			if (isStop(atlas, previous, pageCount)) {
				return previous;
			}
		}
		return -1;
	}

	/**
	 * The page turning forward or back lands on, or -1 for none. Turning starts
	 * from the spread rather than the page, so a book open on a map page (as a
	 * lectern can be) turns to the next spread, not to the map's own notes.
	 */
	public static int turnTarget(Atlas atlas, int page, int pageCount, boolean forward) {
		int from = page;
		int partner = Atlas.partner(page);
		if (!isStop(atlas, page, pageCount) && partner < pageCount && isStop(atlas, partner, pageCount)) {
			from = partner;
		}
		return forward ? nextStop(atlas, from, pageCount) : previousStop(atlas, from, pageCount);
	}

	/** The first page to show when the book opens. */
	public static int firstStop(Atlas atlas, int page, int pageCount) {
		if (isStop(atlas, page, pageCount)) {
			return page;
		}
		int next = nextStop(atlas, page, pageCount);
		if (next >= 0) {
			return next;
		}
		int previous = previousStop(atlas, page, pageCount);
		return previous >= 0 ? previous : page;
	}

	/** Draws the vanilla book page, mirrored when it's the left page of the spread. */
	public static void drawPage(GuiGraphicsExtractor graphics, Identifier texture, int left, int top, boolean mirrored) {
		if (mirrored) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture, left - MIRROR_OFFSET, top,
					PAGE_TEXTURE, 0, PAGE_TEXTURE, PAGE_TEXTURE, -PAGE_TEXTURE, PAGE_TEXTURE, 256, 256);
		} else {
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture, left, top, 0, 0, PAGE_TEXTURE, PAGE_TEXTURE, 256, 256);
		}
	}

	/**
	 * Draws what the spread adds to the vanilla page at {@code left, top}: the
	 * facing page, and any maps.
	 *
	 * @param text     the text of a book page, for a facing text page
	 * @param sketches the sketch on a book page, or null, for a facing sketched page
	 */
	public static void drawSpread(GuiGraphicsExtractor graphics, Layout layout, Atlas atlas, int left, int top,
			IntFunction<Component> text, IntFunction<byte[]> sketches) {
		if (layout.here() != null) {
			drawMap(graphics, layout.here(), left, top, layout.mirrored());
		}
		if (layout.facing() < 0) {
			return;
		}
		boolean facingMirrored = !layout.facingRight();
		int facingLeft = left + (layout.facingRight() ? FACING_OFFSET : -FACING_OFFSET);
		drawPage(graphics, BookViewScreen.BOOK_LOCATION, facingLeft, top, facingMirrored);
		Atlas.Plate facingMap = atlas.plateAt(layout.facing());
		byte[] sketch = sketches.apply(layout.facing());
		if (facingMap != null) {
			drawMap(graphics, facingMap, facingLeft, top, facingMirrored);
		} else {
			if (sketch != null) {
				drawSketch(graphics, sketch, facingLeft, top, facingMirrored);
			}
			drawText(graphics, text.apply(layout.facing()), facingLeft, top, facingMirrored);
		}
	}

	/** A sketch on a page drawn at {@code left, top}, where the page's text would be. */
	public static void drawSketch(GuiGraphicsExtractor graphics, byte[] sketch, int left, int top, boolean mirrored) {
		SketchView.draw(graphics, sketch, left + BookViewScreen.PAGE_TEXT_X_OFFSET + (mirrored ? MIRROR_OFFSET : 0),
				top + BookViewScreen.PAGE_TEXT_Y_OFFSET);
	}

	/** A map pasted onto a page, on its own paper, with its name above it. */
	private static void drawMap(GuiGraphicsExtractor graphics, Atlas.Plate plate, int left, int top, boolean mirrored) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		int paperLeft = left + (mirrored ? 35 : 30);
		int x = paperLeft + 1;
		int y = top + PASTED_TOP;
		Font font = minecraft.font;
		String name = plate.mapStack(minecraft.level.registryAccess()).getHoverName().getString();
		graphics.text(font, font.plainSubstrByWidth(name, PASTED_SIZE), x + 1, top + TITLE_TOP, INK, false);

		graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_BACKGROUND, x, y, 0, 0, PASTED_SIZE, PASTED_SIZE, 64, 64, 64, 64);
		MapItemSavedData data = minecraft.level.getMapData(plate.id());
		if (data == null) {
			return;
		}
		float scale = PASTED_SIZE / 142.0F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 7 * scale, y + 7 * scale);
		graphics.pose().scale(scale, scale);
		MapRenderState state = new MapRenderState();
		minecraft.getMapRenderer().extractRenderState(plate.id(), data, state);
		graphics.map(state);
		graphics.pose().popMatrix();
	}

	private static void drawText(GuiGraphicsExtractor graphics, Component page, int left, int top, boolean mirrored) {
		Font font = Minecraft.getInstance().font;
		int x = left + 36 + (mirrored ? MIRROR_OFFSET : 0);
		List<FormattedCharSequence> lines = font.split(page, TEXT_WIDTH);
		for (int i = 0; i < lines.size() && i < 14; i++) {
			graphics.text(font, lines.get(i), x, top + TEXT_TOP + i * font.lineHeight, INK, false);
		}
	}
}
