package com.assilf.mapplus.client.atlas;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.client.ink.InkScreen;
import com.assilf.mapplus.net.AtlasActionPayload;
import com.assilf.mapplus.net.OpenInkPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;
import java.util.Optional;

/**
 * An open atlas: each spread shows a page of the book's notes on the left and
 * a map or sketch on the right.
 */
public class AtlasScreen extends Screen {
	private static final int PAPER = 0xFFE9DFC4;
	private static final int PAPER_EDGE = 0xFF6B5A3E;
	private static final int INK = 0xFF3A3020;
	private static final int FADED = 0xFF8A7A5E;
	private static final int RIBBON_WIDTH = 8;

	private final InteractionHand hand;
	private final MapRenderState mapRenderState = new MapRenderState();
	private int page;

	private int pageSize;
	private int leftX;
	private int rightX;
	private int top;

	private Button previous;
	private Button next;
	private Button select;
	private Button ink;
	private Button write;
	private Button sketch;
	private Button takeOut;
	private Button bookmark;

	public AtlasScreen(InteractionHand hand) {
		super(Component.translatable("item.mapplus.atlas"));
		this.hand = hand;
	}

	private ItemStack stack() {
		Player player = minecraft.player;
		return player == null ? ItemStack.EMPTY : player.getItemInHand(hand);
	}

	private Optional<Atlas> atlas() {
		return Atlas.read(stack());
	}

	@Override
	protected void init() {
		pageSize = Math.max(96, Math.min(168, Math.min((width - 24) / 2, height - 76)));
		leftX = width / 2 - pageSize - 2;
		rightX = width / 2 + 2;
		top = Math.max(18, (height - pageSize - 52) / 2);
		atlas().ifPresent(atlas -> page = Math.min(atlas.selected(), Math.max(0, atlas.plates().size() - 1)));

		int row = top + pageSize + 6;
		previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
				.bounds(leftX, row, 20, 20).build());
		next = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
				.bounds(rightX + pageSize - 20, row, 20, 20).build());

		int buttons = 6;
		int gap = 2;
		int total = pageSize * 2 + 4;
		int buttonWidth = (total - gap * (buttons - 1)) / buttons;
		int x = leftX;
		int row2 = row + 22;
		select = addRenderableWidget(Button.builder(Component.translatable("mapplus.atlas.select"),
				b -> send(AtlasActionPayload.Action.SELECT, 0)).bounds(x, row2, buttonWidth, 20).build());
		x += buttonWidth + gap;
		ink = addRenderableWidget(Button.builder(Component.translatable("mapplus.ink.button"),
				b -> openInk()).bounds(x, row2, buttonWidth, 20).build());
		x += buttonWidth + gap;
		write = addRenderableWidget(Button.builder(Component.translatable("mapplus.atlas.write"),
				b -> openNotes()).bounds(x, row2, buttonWidth, 20).build());
		x += buttonWidth + gap;
		sketch = addRenderableWidget(Button.builder(Component.translatable("mapplus.atlas.sketch"),
				b -> send(AtlasActionPayload.Action.ADD_SKETCH, 0)).bounds(x, row2, buttonWidth, 20).build());
		x += buttonWidth + gap;
		takeOut = addRenderableWidget(Button.builder(Component.translatable("mapplus.atlas.take_out"),
				b -> send(AtlasActionPayload.Action.TAKE_OUT, 0)).bounds(x, row2, buttonWidth, 20).build());
		x += buttonWidth + gap;
		bookmark = addRenderableWidget(Button.builder(Component.translatable("mapplus.atlas.bookmark"),
				b -> toggleBookmark()).bounds(x, row2, buttonWidth, 20).build());
		updateButtons();
	}

	@Override
	public void tick() {
		if (atlas().isEmpty()) {
			onClose();
			return;
		}
		updateButtons();
	}

	private void updateButtons() {
		Optional<Atlas> read = atlas();
		if (read.isEmpty() || select == null) {
			return;
		}
		Atlas atlas = read.get();
		int pages = atlas.plates().size();
		page = Math.max(0, Math.min(page, pages - 1));
		boolean editable = Atlas.isEditable(stack());
		boolean hasPage = pages > 0;

		previous.active = page > 0;
		next.active = page < pages - 1;
		select.active = hasPage && atlas.selected() != page;
		select.setMessage(Component.translatable(atlas.selected() == page ? "mapplus.atlas.selected" : "mapplus.atlas.select"));
		ink.active = editable && hasPage;
		write.active = editable;
		sketch.active = editable && pages < Atlas.MAX_PLATES && (creative() || count(Items.PAPER) > 0);
		takeOut.active = editable && hasPage;
		bookmark.active = editable && hasPage && (bookmarkOn(atlas, page) != null || ribbonDye() != null);
	}

	private void turn(int delta) {
		page += delta;
		updateButtons();
	}

	private void send(AtlasActionPayload.Action action, int arg) {
		ClientPlayNetworking.send(new AtlasActionPayload(hand == InteractionHand.OFF_HAND, action, page, arg));
	}

	private void openInk() {
		atlas().flatMap(atlas -> page < atlas.plates().size() ? Optional.of(atlas.plates().get(page)) : Optional.empty())
				.ifPresent(plate -> {
					ClientPlayNetworking.send(new OpenInkPayload(plate.mapId()));
					minecraft.gui.setScreen(new InkScreen(this, plate.mapId()));
				});
	}

	private void openNotes() {
		Player player = minecraft.player;
		ItemStack stack = stack();
		if (player != null && stack.is(Items.WRITABLE_BOOK)) {
			WritableBookContent content = stack.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
			minecraft.gui.setScreen(new BookEditScreen(player, stack, hand, content));
		}
	}

	private void toggleBookmark() {
		atlas().ifPresent(atlas -> {
			if (bookmarkOn(atlas, page) != null) {
				send(AtlasActionPayload.Action.BOOKMARK, -1);
				return;
			}
			DyeColor color = ribbonDye();
			if (color != null) {
				send(AtlasActionPayload.Action.BOOKMARK, color.getId());
			}
		});
	}

	private static DyeColor bookmarkOn(Atlas atlas, int page) {
		for (Atlas.Bookmark mark : atlas.bookmarks()) {
			if (mark.page() == page) {
				return mark.color();
			}
		}
		return null;
	}

	/** The dye a new ribbon would use: the first dye found in the inventory. */
	private DyeColor ribbonDye() {
		if (creative()) {
			return DyeColor.RED;
		}
		for (DyeColor color : DyeColor.values()) {
			if (count(Items.DYE.pick(color)) > 0) {
				return color;
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			Optional<Atlas> read = atlas();
			if (read.isPresent()) {
				List<Atlas.Bookmark> marks = read.get().bookmarks();
				for (int i = 0; i < marks.size(); i++) {
					int rx = ribbonX(i);
					if (event.x() >= rx && event.x() < rx + RIBBON_WIDTH && event.y() >= top - 10 && event.y() < top + 4) {
						page = marks.get(i).page();
						updateButtons();
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private int ribbonX(int index) {
		return rightX + pageSize - 14 - index * (RIBBON_WIDTH + 3);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// Rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		Optional<Atlas> read = atlas();
		if (read.isEmpty()) {
			return;
		}
		Atlas atlas = read.get();

		drawPaper(graphics, leftX, top);
		drawPaper(graphics, rightX, top);
		graphics.verticalLine(width / 2, top, top + pageSize, PAPER_EDGE);

		Component name = stack().getHoverName();
		graphics.centeredText(font, name, width / 2, top - 22, 0xFFFFFFFF);

		if (atlas.plates().isEmpty()) {
			graphics.centeredText(font, Component.translatable("mapplus.atlas.empty"), rightX + pageSize / 2, top + pageSize / 2, FADED);
		} else {
			drawNotes(graphics);
			drawPlate(graphics, atlas);
			drawRibbons(graphics, atlas);
			Component number = Component.translatable("mapplus.atlas.page", page + 1, atlas.plates().size());
			graphics.centeredText(font, number, width / 2, top + pageSize + 12, 0xFFFFFFFF);
		}
	}

	private void drawPaper(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x - 1, y - 1, x + pageSize + 1, y + pageSize + 1, PAPER_EDGE);
		graphics.fill(x, y, x + pageSize, y + pageSize, PAPER);
	}

	private void drawNotes(GuiGraphicsExtractor graphics) {
		BookViewScreen.BookAccess notes = BookViewScreen.BookAccess.fromItem(stack());
		int margin = 8;
		if (page >= notes.getPageCount()) {
			graphics.text(font, Component.translatable("mapplus.atlas.no_notes"), leftX + margin, top + margin, FADED, false);
			return;
		}
		List<FormattedCharSequence> lines = font.split(notes.getPage(page), pageSize - margin * 2);
		int y = top + margin;
		for (FormattedCharSequence line : lines) {
			if (y > top + pageSize - margin - font.lineHeight) {
				break;
			}
			graphics.text(font, line, leftX + margin, y, INK, false);
			y += font.lineHeight;
		}
	}

	private void drawPlate(GuiGraphicsExtractor graphics, Atlas atlas) {
		Atlas.Plate plate = atlas.plates().get(page);
		int margin = 6;
		int size = pageSize - margin * 2;
		MapItemSavedData data = minecraft.level == null ? null : minecraft.level.getMapData(plate.id());
		if (data == null) {
			graphics.centeredText(font, Component.translatable("mapplus.atlas.unrolling"), rightX + pageSize / 2, top + pageSize / 2, FADED);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(rightX + margin, top + margin);
		float scale = size / 128.0F;
		graphics.pose().scale(scale, scale);
		minecraft.getMapRenderer().extractRenderState(plate.id(), data, mapRenderState);
		graphics.map(mapRenderState);
		graphics.pose().popMatrix();

		if (atlas.selected() == page) {
			graphics.text(font, Component.translatable("mapplus.atlas.exploring"), rightX + margin, top + pageSize - margin - font.lineHeight, INK, false);
		}
	}

	private void drawRibbons(GuiGraphicsExtractor graphics, Atlas atlas) {
		List<Atlas.Bookmark> marks = atlas.bookmarks();
		for (int i = 0; i < marks.size(); i++) {
			Atlas.Bookmark mark = marks.get(i);
			int x = ribbonX(i);
			int length = mark.page() == page ? 16 : 10;
			int color = 0xFF000000 | mark.color().getTextureDiffuseColor();
			graphics.fill(x, top - 10, x + RIBBON_WIDTH, top - 10 + length, color);
		}
	}

	// Supplies

	private boolean creative() {
		Player player = minecraft.player;
		return player != null && player.isCreative();
	}

	private int count(net.minecraft.world.item.Item item) {
		Player player = minecraft.player;
		if (player == null) {
			return 0;
		}
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
