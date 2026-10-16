package com.assilf.mapplus.client.desk;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.client.atlas.AtlasSpread;
import com.assilf.mapplus.client.atlas.OpenBook;
import com.assilf.mapplus.ink.DrawingDesk;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The book on the desk: with a map and a book and quill, the book lies open
 * where the map lands, exactly as the result will be; with an atlas on top,
 * at the spread whose map shears would cut out. Its arrows turn it, as a
 * lectern's do; the table keeps the page. Hovering a page shows its first
 * lines, or its map's name.
 */
final class DeskBook {
	/** The spread shrunk to the canvas's width, and where it sits in the canvas. */
	private static final float SCALE = 128.0F / (OpenBook.MAX_X - OpenBook.MIN_X);
	private static final int SPREAD_TOP = 10;
	private static final int ARROWS_Y = 106;
	private static final int ARROW_WIDTH = 23;
	private static final int ARROW_HEIGHT = 13;
	private static final int BACK = 1;
	private static final int FORWARD = 2;
	private static final int TOOLTIP_LINES = 4;
	private static final Identifier FORWARD_SPRITE = Identifier.withDefaultNamespace("widget/page_forward");
	private static final Identifier FORWARD_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/page_forward_highlighted");
	private static final Identifier BACK_SPRITE = Identifier.withDefaultNamespace("widget/page_backward");
	private static final Identifier BACK_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/page_backward_highlighted");

	private final Minecraft minecraft = Minecraft.getInstance();
	private final CartographyTableMenu menu;

	DeskBook(CartographyTableMenu menu) {
		this.menu = menu;
	}

	private ItemStack top() {
		return menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem();
	}

	private ItemStack second() {
		return menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem();
	}

	private boolean cutting() {
		return Atlas.canCut(top());
	}

	/** Whether the desk shows a book instead of a map. */
	boolean shown() {
		return cutting() || top().is(Items.FILLED_MAP) && second().is(Items.WRITABLE_BOOK);
	}

	/** The book as shown: the stitched result where the map lands, or the book the table turns. */
	private ItemStack book() {
		if (cutting()) {
			return top();
		}
		ItemStack result = menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem();
		return result.is(Items.WRITABLE_BOOK) ? result : second();
	}

	private int page() {
		ItemStack result = menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem();
		if (!cutting() && result.is(Items.WRITABLE_BOOK)) {
			// The result is bookmarked at its new map.
			return Atlas.read(result).map(Atlas::selected).orElse(0);
		}
		return Math.max(0, DrawingDesk.of(menu).mapplus$page());
	}

	private int pageCount(ItemStack book) {
		Atlas atlas = Atlas.read(book).orElse(Atlas.EMPTY);
		return Math.max(1, Atlas.pages(book, atlas).size());
	}

	void extract(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		graphics.fill(x, y, x + 128, y + 128, 0xFF6B5A43);
		ItemStack book = book();
		int page = page();
		graphics.pose().pushMatrix();
		graphics.pose().translate(x - OpenBook.MIN_X * SCALE, y + SPREAD_TOP - OpenBook.MIN_Y * SCALE);
		graphics.pose().scale(SCALE, SCALE);
		AtlasSpread.drawBook(graphics, book, page, 0, 0);
		graphics.pose().popMatrix();

		boolean overBack = over(mouseX, mouseY, x + 4, y + ARROWS_Y);
		boolean overForward = over(mouseX, mouseY, x + 128 - 4 - ARROW_WIDTH, y + ARROWS_Y);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overBack ? BACK_HIGHLIGHTED : BACK_SPRITE, x + 4, y + ARROWS_Y, ARROW_WIDTH, ARROW_HEIGHT);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overForward ? FORWARD_HIGHLIGHTED : FORWARD_SPRITE, x + 128 - 4 - ARROW_WIDTH,
				y + ARROWS_Y, ARROW_WIDTH, ARROW_HEIGHT);
		int first = (page & ~1) + 1;
		Component indicator = Component.translatable("book.pageIndicator", first, pageCount(book));
		graphics.text(minecraft.font, indicator, x + 64 - minecraft.font.width(indicator) / 2, y + ARROWS_Y + 3, 0xFFF0E2C0, true);
		Component hint = Component.translatableWithFallback(cutting() ? "mapplus.desk.book.cut" : "mapplus.desk.book.stitch",
				cutting() ? "Shears cut this map out" : "The map goes here");
		graphics.text(minecraft.font, hint, x + 64 - minecraft.font.width(hint) / 2, y + 96, 0xFFF0E2C0, true);
	}

	private static boolean over(double mouseX, double mouseY, int x, int y) {
		return mouseX >= x && mouseY >= y && mouseX < x + ARROW_WIDTH && mouseY < y + ARROW_HEIGHT;
	}

	/** Turns the page with the arrows; any press on the book is the book's. */
	boolean mouseClicked(double mouseX, double mouseY, int x, int y) {
		if (mouseX < x || mouseY < y || mouseX >= x + 128 || mouseY >= y + 128) {
			return false;
		}
		int button = over(mouseX, mouseY, x + 4, y + ARROWS_Y) ? BACK : over(mouseX, mouseY, x + 128 - 4 - ARROW_WIDTH, y + ARROWS_Y) ? FORWARD : 0;
		if (button != 0 && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
		}
		return true;
	}

	/** The first lines of the page under the mouse, or its map's name. */
	void tooltip(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		if (mouseX < x || mouseX >= x + 128 || mouseY < y + SPREAD_TOP || mouseY >= y + SPREAD_TOP + (OpenBook.MAX_Y - OpenBook.MIN_Y) * SCALE) {
			return;
		}
		ItemStack book = book();
		int index = (page() & ~1) + (mouseX - x >= (OpenBook.MAX_X - OpenBook.MIN_X) / 2 * SCALE ? 1 : 0);
		Atlas atlas = Atlas.read(book).orElse(Atlas.EMPTY);
		List<Component> lines = new ArrayList<>();
		Atlas.Plate plate = atlas.plateAt(index);
		if (plate != null && minecraft.level != null) {
			lines.add(plate.mapStack(minecraft.level.registryAccess()).getHoverName());
		} else {
			BookViewScreen.BookAccess access = BookViewScreen.BookAccess.fromItem(book);
			if (access != null && index < access.pages().size()) {
				for (FormattedCharSequence line : minecraft.font.split(access.pages().get(index), 160)) {
					if (lines.size() >= TOOLTIP_LINES) {
						break;
					}
					StringBuilder text = new StringBuilder();
					line.accept((position, style, codePoint) -> {
						text.appendCodePoint(codePoint);
						return true;
					});
					lines.add(Component.literal(text.toString()));
				}
			}
			if (atlas.sketchAt(index) != null) {
				lines.add(Component.translatableWithFallback("mapplus.desk.book.sketch", "A sketch").withStyle(ChatFormatting.GRAY));
			}
			if (lines.isEmpty()) {
				lines.add(Component.translatableWithFallback("mapplus.desk.book.empty", "An empty page").withStyle(ChatFormatting.GRAY));
			}
		}
		lines.add(0, Component.translatable("book.pageIndicator", index + 1, pageCount(book)).withStyle(ChatFormatting.DARK_GRAY));
		graphics.setTooltipForNextFrame(minecraft.font, lines, Optional.empty(), mouseX, mouseY);
	}
}
