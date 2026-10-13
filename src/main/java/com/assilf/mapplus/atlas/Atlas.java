package com.assilf.mapplus.atlas;

import com.google.common.collect.MapMaker;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An atlas is a vanilla book and quill (or, once signed, a written book) with
 * maps stitched in as pages. The book's {@code custom_data} says which pages
 * hold which map; the pages themselves stay ordinary book pages, so the notes
 * survive if the mod is removed.
 *
 * <p>Pages come in spreads, as in an open book: pages 0 and 1, 2 and 3, and so
 * on. A map page faces the other page of its spread, where its notes go.
 *
 * <p>Any page that isn't a map's may hold a {@link Sketch} instead of text, so
 * a book and quill with sketches and no maps is a sketchbook.
 *
 * @param plates   the stitched maps
 * @param selected the map page that explores while the atlas is held, or -1
 * @param sketches the sketched pages
 */
public record Atlas(List<Plate> plates, int selected, List<Sketch> sketches) {
	public static final String KEY = "mapplus:atlas";
	private static final int VERSION = 2;
	public static final Atlas EMPTY = new Atlas(List.of(), -1, List.of());

	/**
	 * One stitched map.
	 *
	 * @param page the book page the map is on
	 * @param item the map item as it was when stitched, so explorer and treasure
	 *             markers that live on the item keep showing
	 */
	public record Plate(int mapId, int page, Optional<CompoundTag> item) {
		public static final Codec<Plate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("map").forGetter(Plate::mapId),
				Codec.INT.optionalFieldOf("page", -1).forGetter(Plate::page),
				CustomData.COMPOUND_TAG_CODEC.optionalFieldOf("item").forGetter(Plate::item)
		).apply(instance, Plate::new));

		private static final Map<CompoundTag, ItemStack> DECODED = new MapMaker().weakKeys().makeMap();

		/** A page for a map item, keeping the whole item. */
		public static Plate bound(ItemStack map, int page, HolderLookup.Provider registries) {
			MapId id = map.get(DataComponents.MAP_ID);
			Optional<CompoundTag> item = ItemStack.CODEC
					.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), map.copyWithCount(1))
					.result()
					.filter(CompoundTag.class::isInstance)
					.map(CompoundTag.class::cast);
			return new Plate(id == null ? 0 : id.id(), page, item);
		}

		public MapId id() {
			return new MapId(mapId);
		}

		/**
		 * The map item for this page: the item as stitched, or a plain filled map.
		 * The returned stack is shared; copy it before handing it out.
		 */
		public ItemStack mapStack(HolderLookup.Provider registries) {
			ItemStack stack = item.map(tag -> DECODED.computeIfAbsent(tag, t -> ItemStack.CODEC
					.parse(RegistryOps.create(NbtOps.INSTANCE, registries), t)
					.result()
					.orElse(ItemStack.EMPTY))).orElse(ItemStack.EMPTY);
			if (!stack.is(Items.FILLED_MAP) || !id().equals(stack.get(DataComponents.MAP_ID))) {
				stack = new ItemStack(Items.FILLED_MAP);
				stack.set(DataComponents.MAP_ID, id());
			}
			return stack;
		}
	}

	public static final Codec<Atlas> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("version", VERSION).forGetter(atlas -> VERSION),
			Plate.CODEC.listOf().optionalFieldOf("maps", List.of()).forGetter(Atlas::plates),
			Codec.INT.optionalFieldOf("selected", -1).forGetter(Atlas::selected),
			Sketch.CODEC.listOf().optionalFieldOf("sketches", List.of()).forGetter(Atlas::sketches)
	).apply(instance, (version, plates, selected, sketches) -> new Atlas(plates, selected, sketches)));

	public Atlas {
		plates = List.copyOf(plates);
		sketches = List.copyOf(sketches);
	}

	public Atlas(List<Plate> plates, int selected) {
		this(plates, selected, List.of());
	}

	public static boolean isBook(ItemStack stack) {
		return stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK);
	}

	/** Parsed atlases, by item data. Item data never changes in place, so this never goes stale. */
	private static final Map<CustomData, Optional<Atlas>> PARSED = new MapMaker().weakKeys().makeMap();

	public static Optional<Atlas> read(ItemStack stack) {
		if (!isBook(stack)) {
			return Optional.empty();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return Optional.empty();
		}
		return PARSED.computeIfAbsent(data, Atlas::parse);
	}

	private static Optional<Atlas> parse(CustomData data) {
		Tag tag = data.copyTag().get(KEY);
		if (tag == null) {
			return Optional.empty();
		}
		return CODEC.parse(NbtOps.INSTANCE, tag).result();
	}

	public static boolean isAtlas(ItemStack stack) {
		return read(stack).isPresent();
	}

	/** Whether this is the atlas itself rather than a copy of a signed one. */
	public static boolean isOriginal(ItemStack stack) {
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return written == null || written.generation() == 0;
	}

	public void write(ItemStack stack) {
		Tag tag = CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(KEY, tag));
	}

	/** The other page of the spread a page is in. */
	public static int partner(int page) {
		return page ^ 1;
	}

	public boolean contains(MapId mapId) {
		for (Plate plate : plates) {
			if (plate.mapId() == mapId.id()) {
				return true;
			}
		}
		return false;
	}

	/** The map on a page, or null if the page is a text page. */
	public Plate plateAt(int page) {
		for (Plate plate : plates) {
			if (plate.page() == page) {
				return plate;
			}
		}
		return null;
	}

	public boolean isMapPage(int page) {
		return page >= 0 && plateAt(page) != null;
	}

	/** The map shown in the spread of a page: on the page itself, or facing it. */
	public Plate mapInSpread(int page) {
		Plate here = plateAt(page);
		return here != null ? here : plateAt(partner(page));
	}

	public Optional<Plate> selectedPlate() {
		return selected < 0 ? Optional.empty() : Optional.ofNullable(plateAt(selected));
	}

	public Atlas withSelected(int page) {
		return new Atlas(plates, page, sketches);
	}

	/** The sketch on a page, or null. */
	public Sketch sketchAt(int page) {
		for (Sketch sketch : sketches) {
			if (sketch.page() == page) {
				return sketch;
			}
		}
		return null;
	}

	/** The atlas with a page's sketch replaced; a blank sketch takes the page's sketch away. */
	public Atlas withSketch(Sketch sketch) {
		List<Sketch> changed = new ArrayList<>();
		for (Sketch old : sketches) {
			if (old.page() != sketch.page()) {
				changed.add(old);
			}
		}
		if (!sketch.isBlank()) {
			changed.add(sketch);
		}
		return new Atlas(plates, selected, changed);
	}

	/**
	 * How many pages the book needs so that every map has the other page of its
	 * spread, and every sketch its page.
	 */
	public int requiredPages() {
		int required = 0;
		for (Plate plate : plates) {
			if (plate.page() >= 0) {
				required = Math.max(required, (plate.page() | 1) + 1);
			}
		}
		for (Sketch sketch : sketches) {
			required = Math.max(required, sketch.page() + 1);
		}
		return required;
	}

	/**
	 * Stitches a map into a book and quill (or an atlas) as its next page. If the
	 * book ends on a left page, the map goes on the right, facing it. Otherwise
	 * the map starts a new spread on the left, with an empty page for notes on
	 * the right.
	 *
	 * @return the atlas, or an empty stack if the map can't be stitched in
	 */
	public static ItemStack stitch(ItemStack book, ItemStack map, HolderLookup.Provider registries) {
		MapId mapId = map.get(DataComponents.MAP_ID);
		if (mapId == null || !map.is(Items.FILLED_MAP) || !book.is(Items.WRITABLE_BOOK)) {
			return ItemStack.EMPTY;
		}
		Atlas atlas = read(book).orElse(EMPTY);
		if (atlas.contains(mapId)) {
			return ItemStack.EMPTY;
		}

		WritableBookContent content = book.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
		List<Filterable<String>> pages = new ArrayList<>(content.pages());
		while (pages.size() < atlas.requiredPages()) {
			pages.add(Filterable.passThrough(""));
		}
		int mapPage = pages.size();
		boolean newSpread = mapPage % 2 == 0;
		if (mapPage + (newSpread ? 2 : 1) > WritableBookContent.MAX_PAGES) {
			return ItemStack.EMPTY;
		}
		// The map page's own text is what the page shows without the mod.
		pages.add(Filterable.passThrough(map.getHoverName().getString()));
		if (newSpread) {
			pages.add(Filterable.passThrough(""));
		}

		ItemStack out = book.copyWithCount(1);
		out.set(DataComponents.WRITABLE_BOOK_CONTENT, content.withReplacedPages(pages));
		List<Plate> plates = new ArrayList<>(atlas.plates());
		plates.add(Plate.bound(map, mapPage, registries));
		new Atlas(plates, atlas.selected(), atlas.sketches()).write(out);
		if (!out.has(DataComponents.CUSTOM_NAME)) {
			out.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("item.mapplus.atlas", "Atlas"));
		}
		return out;
	}
}
