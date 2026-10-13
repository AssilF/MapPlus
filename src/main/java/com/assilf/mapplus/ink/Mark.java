package com.assilf.mapplus.ink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Something placed on a map for planning: a label (text written with a sign),
 * a pin (a banner, with its design) or an icon (a pottery sherd's picture,
 * stamped in ink). Kept with the map's ink, so every copy of the map shows it.
 *
 * @param id     tells the marks on one map apart
 * @param x      the map pixel the mark is centered on, 0 to 127
 * @param y      the map pixel the mark is centered on, 0 to 127
 * @param color  the label's or icon's ink, or the pin's banner color
 * @param text   the label's text, or the pin's or icon's name (empty for none)
 * @param size   how big it's drawn, {@link #SMALL} to {@link #LARGE}
 * @param author who placed it, for server logs and moderation
 * @param fade   how watered down a label's or icon's ink is, {@link Ink#FULL} to {@link Ink#FAINTEST}
 * @param symbol an icon's picture: the sherd's pottery pattern texture, as in {@code minecraft:skull_pottery_pattern}
 * @param layers a pin's banner patterns, bottom first
 * @param glow   whether a label's or icon's ink glows, so it shines out at night
 */
public record Mark(int id, Kind kind, int x, int y, DyeColor color, String text, int size, Optional<UUID> author,
		int fade, String symbol, List<Layer> layers, boolean glow) {
	public static final int SMALL = 0;
	public static final int MEDIUM = 1;
	public static final int LARGE = 2;
	public static final int MAX_TEXT = 32;
	/** Banners get at most six patterns from a loom; commands can add more. */
	public static final int MAX_LAYERS = 16;
	private static final int MAX_ID_LENGTH = 256;

	public enum Kind {
		LABEL("label"),
		PIN("pin"),
		ICON("icon");

		private final String id;

		Kind(String id) {
			this.id = id;
		}
	}

	/** One pattern on a banner: its texture, as in {@code minecraft:creeper}, and its dye. */
	public record Layer(String asset, DyeColor color) {
		public static final Codec<Layer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("asset").forGetter(Layer::asset),
				DyeColor.CODEC.fieldOf("color").forGetter(Layer::color)
		).apply(instance, Layer::new));
	}

	private static final Codec<Kind> KIND_CODEC = Codec.STRING.xmap(
			name -> {
				for (Kind kind : Kind.values()) {
					if (kind.id.equals(name)) {
						return kind;
					}
				}
				return Kind.LABEL;
			},
			kind -> kind.id
	);

	public static final Codec<Mark> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("id").forGetter(Mark::id),
			KIND_CODEC.fieldOf("kind").forGetter(Mark::kind),
			Codec.INT.fieldOf("x").forGetter(Mark::x),
			Codec.INT.fieldOf("y").forGetter(Mark::y),
			DyeColor.CODEC.optionalFieldOf("color", DyeColor.BLACK).forGetter(Mark::color),
			Codec.STRING.optionalFieldOf("text", "").forGetter(Mark::text),
			Codec.INT.optionalFieldOf("size", MEDIUM).forGetter(Mark::size),
			UUIDUtil.CODEC.optionalFieldOf("author").forGetter(Mark::author),
			Codec.INT.optionalFieldOf("fade", Ink.FULL).forGetter(Mark::fade),
			Codec.STRING.optionalFieldOf("symbol", "").forGetter(Mark::symbol),
			Layer.CODEC.listOf().optionalFieldOf("layers", List.of()).forGetter(Mark::layers),
			Codec.BOOL.optionalFieldOf("glow", false).forGetter(Mark::glow)
	).apply(instance, Mark::new));

	/** Marks as sent to players: without the author. */
	public static final StreamCodec<FriendlyByteBuf, Mark> STREAM_CODEC = StreamCodec.of(
			(buf, mark) -> {
				buf.writeVarInt(mark.id);
				buf.writeByte(mark.kind.ordinal());
				buf.writeByte(mark.x);
				buf.writeByte(mark.y);
				buf.writeByte(mark.color.getId());
				buf.writeUtf(mark.text, MAX_TEXT * 4);
				buf.writeByte(mark.size);
				buf.writeByte(mark.fade);
				buf.writeUtf(mark.symbol, MAX_ID_LENGTH);
				buf.writeByte(mark.layers.size());
				for (Layer layer : mark.layers) {
					buf.writeUtf(layer.asset(), MAX_ID_LENGTH);
					buf.writeByte(layer.color().getId());
				}
				buf.writeBoolean(mark.glow);
			},
			buf -> {
				int id = buf.readVarInt();
				int kind = buf.readUnsignedByte();
				int x = buf.readUnsignedByte();
				int y = buf.readUnsignedByte();
				DyeColor color = readDye(buf);
				String text = buf.readUtf(MAX_TEXT * 4);
				int size = buf.readUnsignedByte();
				int fade = buf.readUnsignedByte();
				String symbol = buf.readUtf(MAX_ID_LENGTH);
				int count = buf.readUnsignedByte();
				if (kind >= Kind.values().length || count > MAX_LAYERS) {
					throw new DecoderException("Bad mark");
				}
				List<Layer> layers = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					String asset = buf.readUtf(MAX_ID_LENGTH);
					layers.add(new Layer(asset, readDye(buf)));
				}
				boolean glow = buf.readBoolean();
				return new Mark(id, Kind.values()[kind], x, y, color, text, size, Optional.empty(), fade, symbol, layers, glow);
			}
	);

	private static DyeColor readDye(FriendlyByteBuf buf) {
		int color = buf.readUnsignedByte();
		if (color >= DyeColor.values().length) {
			throw new DecoderException("Bad mark color " + color);
		}
		return DyeColor.byId(color);
	}

	/** A label or plain pin: full ink, no picture. */
	public Mark(int id, Kind kind, int x, int y, DyeColor color, String text, int size, Optional<UUID> author) {
		this(id, kind, x, y, color, text, size, author, Ink.FULL, "", List.of(), false);
	}

	/** A mark that doesn't glow. */
	public Mark(int id, Kind kind, int x, int y, DyeColor color, String text, int size, Optional<UUID> author, int fade, String symbol,
			List<Layer> layers) {
		this(id, kind, x, y, color, text, size, author, fade, symbol, layers, false);
	}

	public Mark {
		x = clampPixel(x);
		y = clampPixel(y);
		size = clampSize(size);
		text = text == null ? "" : text;
		fade = Ink.clampStrength(fade);
		symbol = symbol == null ? "" : symbol;
		layers = layers == null ? List.of() : List.copyOf(layers.subList(0, Math.min(layers.size(), MAX_LAYERS)));
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

	/** Ink a sherd's icon costs at this size, in pixels. */
	public static int iconCost(int size) {
		return switch (clampSize(size)) {
			case SMALL -> 8;
			case LARGE -> 32;
			default -> 16;
		};
	}

	/** Ink this mark took to make, in pixels: nothing for a pin, which is a banner. */
	public int cost() {
		return switch (kind) {
			case LABEL -> cost(text, size);
			case ICON -> iconCost(size);
			case PIN -> 0;
		};
	}

	public boolean isLabel() {
		return kind == Kind.LABEL;
	}

	/** Whether the mark is made of ink, so water fades it: labels and icons. Pins are banners. */
	public boolean isInk() {
		return kind != Kind.PIN;
	}

	public Mark at(int newX, int newY) {
		return new Mark(id, kind, newX, newY, color, text, size, author, fade, symbol, layers, glow);
	}

	public Mark withSize(int newSize) {
		return new Mark(id, kind, x, y, color, text, newSize, author, fade, symbol, layers, glow);
	}

	public Mark withFade(int newFade) {
		return new Mark(id, kind, x, y, color, text, size, author, newFade, symbol, layers, glow);
	}

	public Mark glowing() {
		return new Mark(id, kind, x, y, color, text, size, author, fade, symbol, layers, true);
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
