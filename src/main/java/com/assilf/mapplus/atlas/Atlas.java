package com.assilf.mapplus.atlas;

import com.google.common.collect.MapMaker;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An atlas is a vanilla book and quill (or, once signed, a written book) whose
 * {@code custom_data} lists the maps bound into it. Its notes are the book's
 * own pages, so they survive if the mod is removed.
 *
 * @param plates    the map pages, in order
 * @param selected  the map page that explores and projects while the atlas is held
 * @param bookmarks dye ribbons marking map pages
 */
public record Atlas(List<Plate> plates, int selected, List<Bookmark> bookmarks) {
	public static final String KEY = "mapplus:atlas";
	public static final int MAX_PLATES = 64;
	private static final int VERSION = 1;

	/**
	 * One map page.
	 *
	 * @param sketch whether this is a blank sketch page rather than a bound map
	 * @param item   the map item as it was when bound, so markers, names and
	 *               colors that live on the item come back when it is taken out
	 */
	public record Plate(int mapId, boolean sketch, Optional<CompoundTag> item) {
		public static final Codec<Plate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("map").forGetter(Plate::mapId),
				Codec.BOOL.optionalFieldOf("sketch", false).forGetter(Plate::sketch),
				CustomData.COMPOUND_TAG_CODEC.optionalFieldOf("item").forGetter(Plate::item)
		).apply(instance, Plate::new));

		private static final Map<CompoundTag, ItemStack> DECODED = new MapMaker().weakKeys().makeMap();

		public Plate(int mapId, boolean sketch) {
			this(mapId, sketch, Optional.empty());
		}

		/** A page for a map item, keeping the whole item. */
		public static Plate bound(ItemStack map, HolderLookup.Provider registries) {
			MapId id = map.get(DataComponents.MAP_ID);
			Optional<CompoundTag> item = ItemStack.CODEC
					.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), map.copyWithCount(1))
					.result()
					.filter(CompoundTag.class::isInstance)
					.map(CompoundTag.class::cast);
			return new Plate(id == null ? 0 : id.id(), false, item);
		}

		public MapId id() {
			return new MapId(mapId);
		}

		/**
		 * The map item for this page: the item as bound, or a plain filled map.
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

	public record Bookmark(int page, DyeColor color) {
		public static final Codec<Bookmark> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("page").forGetter(Bookmark::page),
				DyeColor.CODEC.fieldOf("color").forGetter(Bookmark::color)
		).apply(instance, Bookmark::new));
	}

	public static final Codec<Atlas> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("version", VERSION).forGetter(atlas -> VERSION),
			Plate.CODEC.listOf().optionalFieldOf("pages", List.of()).forGetter(Atlas::plates),
			Codec.INT.optionalFieldOf("selected", 0).forGetter(Atlas::selected),
			Bookmark.CODEC.listOf().optionalFieldOf("bookmarks", List.of()).forGetter(Atlas::bookmarks)
	).apply(instance, (version, plates, selected, bookmarks) -> new Atlas(plates, selected, bookmarks)));

	public Atlas {
		plates = List.copyOf(plates);
		bookmarks = List.copyOf(bookmarks);
		selected = plates.isEmpty() ? 0 : Math.max(0, Math.min(selected, plates.size() - 1));
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

	/** Whether the atlas can still be changed: signed atlases are read-only. */
	public static boolean isEditable(ItemStack stack) {
		return stack.is(Items.WRITABLE_BOOK) && isAtlas(stack);
	}

	public void write(ItemStack stack) {
		Tag tag = CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(KEY, tag));
	}

	public boolean contains(MapId mapId) {
		for (Plate plate : plates) {
			if (plate.mapId() == mapId.id()) {
				return true;
			}
		}
		return false;
	}

	public Optional<Plate> selectedPlate() {
		return plates.isEmpty() ? Optional.empty() : Optional.of(plates.get(selected));
	}

	public Atlas withPlate(Plate plate) {
		List<Plate> copy = new ArrayList<>(plates);
		copy.add(plate);
		return new Atlas(copy, plates.isEmpty() ? 0 : selected, bookmarks);
	}

	public Atlas withoutPlate(int page) {
		List<Plate> copy = new ArrayList<>(plates);
		copy.remove(page);
		List<Bookmark> marks = new ArrayList<>();
		for (Bookmark mark : bookmarks) {
			if (mark.page() < page) {
				marks.add(mark);
			} else if (mark.page() > page) {
				marks.add(new Bookmark(mark.page() - 1, mark.color()));
			}
		}
		int newSelected = selected > page ? selected - 1 : selected;
		return new Atlas(copy, newSelected, marks);
	}

	public Atlas withSelected(int page) {
		return new Atlas(plates, page, bookmarks);
	}

	/** Sets the ribbon on a page, or removes it when color is null. */
	public Atlas withBookmark(int page, DyeColor color) {
		List<Bookmark> marks = new ArrayList<>();
		for (Bookmark mark : bookmarks) {
			if (mark.page() != page) {
				marks.add(mark);
			}
		}
		if (color != null) {
			marks.add(new Bookmark(page, color));
		}
		marks.sort((a, b) -> Integer.compare(a.page(), b.page()));
		return new Atlas(plates, selected, marks);
	}

	public static Atlas of(Plate first) {
		return new Atlas(List.of(first), 0, List.of());
	}

	/**
	 * Notes are paired with map pages by position. When a map page is taken out,
	 * its note page moves to the end of the book so the other notes stay beside
	 * their maps and nothing written is lost.
	 */
	public static WritableBookContent withNoteMovedToEnd(WritableBookContent content, int page) {
		List<Filterable<String>> pages = new ArrayList<>(content.pages());
		if (page < 0 || page >= pages.size()) {
			return content;
		}
		Filterable<String> note = pages.remove(page);
		if (!note.raw().isEmpty()) {
			pages.add(note);
		}
		return content.withReplacedPages(pages);
	}
}
