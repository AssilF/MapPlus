package com.assilf.mapplus.ink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The ink drawn over one map, and the labels and pins placed on it. None of it
 * touches the map's own data.
 */
public final class InkLayer {
	private static final Codec<byte[]> PIXELS_CODEC = Codec.BYTE_BUFFER.xmap(
			buffer -> {
				byte[] pixels = new byte[Ink.PIXELS];
				ByteBuffer copy = buffer.duplicate();
				copy.get(pixels, 0, Math.min(copy.remaining(), Ink.PIXELS));
				return pixels;
			},
			ByteBuffer::wrap
	);

	public static final Codec<InkLayer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			PIXELS_CODEC.fieldOf("pixels").forGetter(layer -> layer.pixels),
			Codec.BOOL.optionalFieldOf("sealed", false).forGetter(layer -> layer.sealed),
			UUIDUtil.CODEC.optionalFieldOf("sealer").forGetter(layer -> Optional.ofNullable(layer.sealer)),
			Mark.CODEC.listOf().optionalFieldOf("marks", List.of()).forGetter(layer -> layer.marks)
	).apply(instance, InkLayer::new));

	private final byte[] pixels;
	private boolean sealed;
	/** Who sealed the map, if anyone has since seals had owners. */
	private UUID sealer;
	private final List<Mark> marks;
	/** Changes whenever the marks do, so each player is sent them once per change. */
	private int marksVersion = 1;
	/** Changes whenever the ink does. */
	private int inkVersion = 1;

	public InkLayer() {
		this(new byte[Ink.PIXELS], false, Optional.empty(), List.of());
	}

	private InkLayer(byte[] pixels, boolean sealed, Optional<UUID> sealer, List<Mark> marks) {
		this.pixels = pixels;
		this.sealed = sealed;
		this.sealer = sealer.orElse(null);
		this.marks = new ArrayList<>(marks);
	}

	public byte get(int index) {
		return pixels[index];
	}

	public void set(int index, byte ink) {
		if (pixels[index] != ink) {
			pixels[index] = ink;
			inkVersion++;
		}
	}

	public int inkVersion() {
		return inkVersion;
	}

	public byte[] pixels() {
		return pixels;
	}

	public boolean isSealed() {
		return sealed;
	}

	public void setSealed(boolean sealed) {
		this.sealed = sealed;
		if (!sealed) {
			sealer = null;
		}
	}

	public UUID sealer() {
		return sealer;
	}

	public void setSealer(UUID sealer) {
		this.sealer = sealer;
	}

	/** Whether there's no ink on the map. Marks don't count. */
	public boolean isEmpty() {
		for (byte pixel : pixels) {
			if (pixel != Ink.NONE) {
				return false;
			}
		}
		return true;
	}

	/** Whether there's anything worth saving: ink, marks or a seal. */
	public boolean isBlank() {
		return !sealed && marks.isEmpty() && isEmpty();
	}

	/** Erases all ink, labels included. Pins stay: they're banners, not ink. */
	public void clear() {
		Arrays.fill(pixels, Ink.NONE);
		inkVersion++;
		if (marks.removeIf(Mark::isLabel)) {
			marksVersion++;
		}
	}

	/** Whether there's anything water would wash off: ink or labels. */
	public boolean hasInk() {
		return !isEmpty() || countMarks(Mark.Kind.LABEL) > 0;
	}

	public List<Mark> marks() {
		return marks;
	}

	public int marksVersion() {
		return marksVersion;
	}

	public Mark findMark(int id) {
		for (Mark mark : marks) {
			if (mark.id() == id) {
				return mark;
			}
		}
		return null;
	}

	public int countMarks(Mark.Kind kind) {
		int count = 0;
		for (Mark mark : marks) {
			if (mark.kind() == kind) {
				count++;
			}
		}
		return count;
	}

	public int nextMarkId() {
		int max = 0;
		for (Mark mark : marks) {
			max = Math.max(max, mark.id());
		}
		return max + 1;
	}

	/** Adds a mark, or replaces the one with the same id. */
	public void putMark(Mark mark) {
		for (int i = 0; i < marks.size(); i++) {
			if (marks.get(i).id() == mark.id()) {
				marks.set(i, mark);
				marksVersion++;
				return;
			}
		}
		marks.add(mark);
		marksVersion++;
	}

	public boolean removeMark(int id) {
		boolean removed = marks.removeIf(mark -> mark.id() == id);
		if (removed) {
			marksVersion++;
		}
		return removed;
	}

	/**
	 * Draws this layer over a rectangle of map colors, in place.
	 */
	public void overlay(int startX, int startY, int width, int height, byte[] colors) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				byte ink = pixels[(startX + x) + (startY + y) * Ink.SIZE];
				if (Ink.isPresent(ink)) {
					colors[x + y * width] = Ink.toMapColor(ink);
				}
			}
		}
	}
}
