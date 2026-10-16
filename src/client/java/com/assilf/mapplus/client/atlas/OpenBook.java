package com.assilf.mapplus.client.atlas;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.atlas.Sketch;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A book shown open in the world, as the book screen lays it out: the spread
 * it was last read at, with its writing, its sketches and its maps. A book in
 * hand is held open as a map is, and a lectern's book lies open on it.
 * Drawn in book screen pixels, x to the right and y down, seen from -z.
 */
public final class OpenBook {
	/** Where the left page sits, mirrored, and the right page. */
	private static final int LEFT = 0;
	private static final int RIGHT = 148;
	private static final int MIRROR_OFFSET = 4;
	private static final int PAGE_TEXTURE = 192;
	/** The paper both pages show, in book screen pixels. */
	public static final float MIN_X = 22;
	public static final float MAX_X = 314;
	public static final float MIN_Y = 1;
	public static final float MAX_Y = 181;
	/** How wide a book is held open, in map pixels; a map is 128. */
	public static final float HELD_WIDTH = 184;
	private static final int TEXT_LEFT = 36;
	private static final int TEXT_TOP = 32;
	private static final int TEXT_WIDTH = 114;
	private static final int LINES = 14;
	private static final int INK = 0xFF000000;
	private static final int PASTED_SIZE = 120;
	private static final int PASTED_TOP = 26;
	private static final int TITLE_TOP = 16;
	/** How far each layer sits above the paper, toward the reader. */
	private static final float LAYER = -0.1F;
	private static final Identifier MAP_BACKGROUND = Identifier.withDefaultNamespace("textures/map/map_background.png");
	/** The most sketches kept as textures at once. */
	private static final int MOST_SKETCHES = 32;

	/** The page each book was last read at, by what it is. */
	private static final Map<String, Integer> READ_AT = new HashMap<>();
	private static final Map<Integer, Identifier> SKETCHES = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, Identifier> eldest) {
			if (size() > MOST_SKETCHES) {
				Minecraft.getInstance().getTextureManager().release(eldest.getValue());
				return true;
			}
			return false;
		}
	};
	private static int sketchCount;

	private OpenBook() {
	}

	/** Whether a stack is a book that shows open in hand. */
	public static boolean isBook(ItemStack stack) {
		return stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK);
	}

	/** Tells books apart well enough to remember where each was read to. */
	private static String key(ItemStack stack) {
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		String name = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : "";
		return written != null ? "written:" + written.title().raw() + "/" + written.author() : "writable:" + name;
	}

	/** Remembers the page a book in hand was closed on. */
	public static void readAt(int page) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = minecraft.player.getItemInHand(hand);
			if (isBook(stack)) {
				READ_AT.put(key(stack), page);
				return;
			}
		}
	}

	/** The page a book in hand opens at: where it was last read, or its chosen map, or the first. */
	public static int page(ItemStack stack) {
		Integer read = READ_AT.get(key(stack));
		if (read != null) {
			return read;
		}
		int selected = Atlas.read(stack).map(Atlas::selected).orElse(-1);
		return Math.max(0, selected);
	}

	/** Draws a book open at a page's spread, in book screen pixels. */
	public static void submit(PoseStack pose, SubmitNodeCollector collector, int light, ItemStack book, int page) {
		Minecraft minecraft = Minecraft.getInstance();
		BookViewScreen.BookAccess access = BookViewScreen.BookAccess.fromItem(book);
		List<Component> pages = access == null ? List.of() : access.pages();
		Atlas atlas = Atlas.read(book).orElse(Atlas.EMPTY);
		int left = Math.max(0, page) & ~1;

		collector.submitCustomGeometry(pose, RenderTypes.text(BookViewScreen.BOOK_LOCATION), (at, vertices) -> {
			float u = PAGE_TEXTURE / 256.0F;
			// The left page, mirrored, and the right one.
			float x0 = LEFT - MIRROR_OFFSET;
			float x1 = x0 + PAGE_TEXTURE;
			vertices.addVertex(at, x0, PAGE_TEXTURE, 0).setColor(-1).setUv(u, u).setLight(light);
			vertices.addVertex(at, x1, PAGE_TEXTURE, 0).setColor(-1).setUv(0, u).setLight(light);
			vertices.addVertex(at, x1, 0, 0).setColor(-1).setUv(0, 0).setLight(light);
			vertices.addVertex(at, x0, 0, 0).setColor(-1).setUv(u, 0).setLight(light);
			x0 = RIGHT;
			x1 = x0 + PAGE_TEXTURE;
			vertices.addVertex(at, x0, PAGE_TEXTURE, 0).setColor(-1).setUv(0, u).setLight(light);
			vertices.addVertex(at, x1, PAGE_TEXTURE, 0).setColor(-1).setUv(u, u).setLight(light);
			vertices.addVertex(at, x1, 0, 0).setColor(-1).setUv(u, 0).setLight(light);
			vertices.addVertex(at, x0, 0, 0).setColor(-1).setUv(0, 0).setLight(light);
		});

		for (int side = 0; side < 2; side++) {
			int index = left + side;
			boolean mirrored = side == 0;
			int pageLeft = mirrored ? LEFT : RIGHT;
			Atlas.Plate plate = atlas.plateAt(index);
			if (plate != null) {
				map(pose, collector, light, plate, pageLeft, mirrored, minecraft);
				continue;
			}
			Sketch sketch = atlas.sketchAt(index);
			if (sketch != null && !sketch.isBlank()) {
				sketch(pose, collector, light, sketch.ink(), pageLeft + BookViewScreen.PAGE_TEXT_X_OFFSET + (mirrored ? MIRROR_OFFSET : 0),
						BookViewScreen.PAGE_TEXT_Y_OFFSET);
			}
			if (index < pages.size()) {
				text(pose, collector, light, pages.get(index), pageLeft + TEXT_LEFT + (mirrored ? MIRROR_OFFSET : 0), TEXT_TOP, minecraft.font);
			}
		}
	}

	private static void text(PoseStack pose, SubmitNodeCollector collector, int light, Component page, float x, float y, Font font) {
		List<FormattedCharSequence> lines = font.split(page, TEXT_WIDTH);
		for (int i = 0; i < lines.size() && i < LINES; i++) {
			pose.pushPose();
			pose.translate(x, y + i * font.lineHeight, 2 * LAYER);
			pose.scale(1.0F, 1.0F, -1.0F);
			collector.order(2).submitText(pose, 0.0F, 0.0F, lines.get(i), false, Font.DisplayMode.NORMAL, light, INK, 0, 0);
			pose.popPose();
		}
	}

	/** A map pasted onto its page, on its own paper, with its name above it, as the book screen shows it. */
	private static void map(PoseStack pose, SubmitNodeCollector collector, int light, Atlas.Plate plate, int pageLeft, boolean mirrored,
			Minecraft minecraft) {
		if (minecraft.level == null) {
			return;
		}
		float x = pageLeft + (mirrored ? 35 : 30) + 1;
		float y = PASTED_TOP;
		String name = plate.mapStack(minecraft.level.registryAccess()).getHoverName().getString();
		FormattedCharSequence title = Component.literal(minecraft.font.plainSubstrByWidth(name, PASTED_SIZE)).getVisualOrderText();
		pose.pushPose();
		pose.translate(x + 1, TITLE_TOP, 2 * LAYER);
		pose.scale(1.0F, 1.0F, -1.0F);
		collector.order(2).submitText(pose, 0.0F, 0.0F, title, false, Font.DisplayMode.NORMAL, light, INK, 0, 0);
		pose.popPose();

		collector.order(1).submitCustomGeometry(pose, RenderTypes.text(MAP_BACKGROUND), (at, vertices) -> {
			vertices.addVertex(at, x, y + PASTED_SIZE, LAYER).setColor(-1).setUv(0, 1).setLight(light);
			vertices.addVertex(at, x + PASTED_SIZE, y + PASTED_SIZE, LAYER).setColor(-1).setUv(1, 1).setLight(light);
			vertices.addVertex(at, x + PASTED_SIZE, y, LAYER).setColor(-1).setUv(1, 0).setLight(light);
			vertices.addVertex(at, x, y, LAYER).setColor(-1).setUv(0, 0).setLight(light);
		});
		MapItemSavedData data = minecraft.level.getMapData(plate.id());
		if (data == null) {
			return;
		}
		float scale = PASTED_SIZE / 142.0F;
		pose.pushPose();
		pose.translate(x + 7 * scale, y + 7 * scale, 2 * LAYER);
		pose.scale(scale, scale, scale);
		MapRenderState state = new MapRenderState();
		minecraft.getMapRenderer().extractRenderState(plate.id(), data, state);
		minecraft.getMapRenderer().render(state, pose, collector, false, light);
		pose.popPose();
	}

	/** A sketch on its page, as one picture. */
	private static void sketch(PoseStack pose, SubmitNodeCollector collector, int light, byte[] ink, float x, float y) {
		Identifier texture = sketchTexture(ink);
		float x1 = x + Sketch.WIDTH;
		float y1 = y + Sketch.HEIGHT;
		collector.order(1).submitCustomGeometry(pose, RenderTypes.text(texture), (at, vertices) -> {
			vertices.addVertex(at, x, y1, LAYER).setColor(-1).setUv(0, 1).setLight(light);
			vertices.addVertex(at, x1, y1, LAYER).setColor(-1).setUv(1, 1).setLight(light);
			vertices.addVertex(at, x1, y, LAYER).setColor(-1).setUv(1, 0).setLight(light);
			vertices.addVertex(at, x, y, LAYER).setColor(-1).setUv(0, 0).setLight(light);
		});
	}

	private static Identifier sketchTexture(byte[] ink) {
		int hash = Arrays.hashCode(ink);
		Identifier texture = SKETCHES.get(hash);
		if (texture != null) {
			return texture;
		}
		NativeImage image = new NativeImage(Sketch.WIDTH, Sketch.HEIGHT, true);
		for (int i = 0; i < Sketch.PIXELS; i++) {
			image.setPixel(i % Sketch.WIDTH, i / Sketch.WIDTH, SketchView.color(ink[i]));
		}
		texture = MapPlus.id("sketch/" + sketchCount++);
		DynamicTexture dynamic = new DynamicTexture(() -> "MapPlus sketch", image);
		dynamic.upload();
		Minecraft.getInstance().getTextureManager().register(texture, dynamic);
		SKETCHES.put(hash, texture);
		return texture;
	}
}
