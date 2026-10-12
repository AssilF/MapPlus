package com.assilf.mapplus.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.ArrayList;
import java.util.List;
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
	 */
	public record Plate(int mapId, boolean sketch) {
		public static final Codec<Plate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("map").forGetter(Plate::mapId),
				Codec.BOOL.optionalFieldOf("sketch", false).forGetter(Plate::sketch)
		).apply(instance, Plate::new));

		public MapId id() {
			return new MapId(mapId);
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

	public static Optional<Atlas> read(ItemStack stack) {
		if (!isBook(stack)) {
			return Optional.empty();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return Optional.empty();
		}
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

	public static Atlas of(MapId first, boolean sketch) {
		return new Atlas(List.of(new Plate(first.id(), sketch)), 0, List.of());
	}
}
