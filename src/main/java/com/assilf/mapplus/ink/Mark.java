package com.assilf.mapplus.ink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;

import java.util.Optional;
import java.util.UUID;

/**
 * Something placed on a map for planning: a label (text written with a sign)
 * or a pin (a banner's marker). Kept with the map's ink, so every copy of the
 * map shows it.
 *
 * @param id     tells the marks on one map apart
 * @param x      the map pixel the mark is centered on, 0 to 127
 * @param y      the map pixel the mark is centered on, 0 to 127
 * @param color  the label's ink, or the pin's banner color
 * @param text   the label's text, or the pin's name (empty for none)
 * @param size   a label's text size, {@link #SMALL} to {@link #LARGE}
 * @param author who placed it, for server logs and moderation
 */
public record Mark(int id, Kind kind, int x, int y, DyeColor color, String text, int size, Optional<UUID> author) {
	public static final int SMALL = 0;
	public static final int MEDIUM = 1;
	public static final int LARGE = 2;
	public static final int MAX_TEXT = 32;

	public enum Kind {
		LABEL,
		PIN
	}

	private static final Codec<Kind> KIND_CODEC = Codec.STRING.xmap(
			name -> "pin".equals(name) ? Kind.PIN : Kind.LABEL,
			kind -> kind == Kind.PIN ? "pin" : "label"
	);

	public static final Codec<Mark> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("id").forGetter(Mark::id),
			KIND_CODEC.fieldOf("kind").forGetter(Mark::kind),
			Codec.INT.fieldOf("x").forGetter(Mark::x),
			Codec.INT.fieldOf("y").forGetter(Mark::y),
			DyeColor.CODEC.optionalFieldOf("color", DyeColor.BLACK).forGetter(Mark::color),
			Codec.STRING.optionalFieldOf("text", "").forGetter(Mark::text),
			Codec.INT.optionalFieldOf("size", MEDIUM).forGetter(Mark::size),
			UUIDUtil.CODEC.optionalFieldOf("author").forGetter(Mark::author)
	).apply(instance, Mark::new));

	/** Marks as sent to players: without the author. */
	public static final StreamCodec<FriendlyByteBuf, Mark> STREAM_CODEC = StreamCodec.of(
			(buf, mark) -> {
				buf.writeVarInt(mark.id);
				buf.writeBoolean(mark.kind == Kind.PIN);
				buf.writeByte(mark.x);
				buf.writeByte(mark.y);
				buf.writeByte(mark.color.getId());
				buf.writeUtf(mark.text, MAX_TEXT * 4);
				buf.writeByte(mark.size);
			},
			buf -> {
				int id = buf.readVarInt();
				Kind kind = buf.readBoolean() ? Kind.PIN : Kind.LABEL;
				int x = buf.readUnsignedByte();
				int y = buf.readUnsignedByte();
				int color = buf.readUnsignedByte();
				String text = buf.readUtf(MAX_TEXT * 4);
				int size = buf.readUnsignedByte();
				if (color >= DyeColor.values().length) {
					throw new DecoderException("Bad mark color " + color);
				}
				return new Mark(id, kind, clampPixel(x), clampPixel(y), DyeColor.byId(color), text, clampSize(size), Optional.empty());
			}
	);

	public Mark {
		x = clampPixel(x);
		y = clampPixel(y);
		size = clampSize(size);
		text = text == null ? "" : text;
	}

	public static int clampPixel(int value) {
		return Math.clamp(value, 0, Ink.SIZE - 1);
	}

	public static int clampSize(int size) {
		return Math.clamp(size, SMALL, LARGE);
	}

	/** Map pixels per font pixel for a label of this size. */
	public static float scale(int size) {
		return switch (clampSize(size)) {
			case SMALL -> 0.5F;
			case LARGE -> 1.0F;
			default -> 0.75F;
		};
	}

	/** Ink a label costs per letter, in pixels, at this size. */
	public static int costPerLetter(int size) {
		return switch (clampSize(size)) {
			case SMALL -> 2;
			case LARGE -> 8;
			default -> 4;
		};
	}

	/** Ink a label costs: its visible letters at its size. Spaces are free. */
	public static int cost(String text, int size) {
		return (int) text.codePoints().filter(c -> !Character.isWhitespace(c)).count() * costPerLetter(size);
	}

	public boolean isLabel() {
		return kind == Kind.LABEL;
	}

	public Mark at(int newX, int newY) {
		return new Mark(id, kind, newX, newY, color, text, size, author);
	}

	/**
	 * Cleans text a player typed: one line, no formatting codes, control or
	 * invisible characters, at most {@link #MAX_TEXT} characters.
	 */
	public static String clean(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		int count = 0;
		int marksInARow = 0;
		for (int c : text.codePoints().toArray()) {
			int type = Character.getType(c);
			if (c == '§' || type == Character.CONTROL || type == Character.FORMAT || type == Character.UNASSIGNED
					|| type == Character.PRIVATE_USE || type == Character.SURROGATE || type == Character.LINE_SEPARATOR
					|| type == Character.PARAGRAPH_SEPARATOR) {
				continue;
			}
			// Accents stack; more than two on one letter is only ever used to deface.
			boolean accent = type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK;
			marksInARow = accent ? marksInARow + 1 : 0;
			if (marksInARow > 2 || count >= MAX_TEXT) {
				continue;
			}
			out.appendCodePoint(Character.isWhitespace(c) ? ' ' : c);
			count++;
		}
		return out.toString().trim().replaceAll(" {2,}", " ");
	}
}
