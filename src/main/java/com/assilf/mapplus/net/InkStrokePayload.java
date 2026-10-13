package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.ink.Ink;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Client applies the material in its cartography table's second slot to some
 * pixels of the map in the first slot.
 *
 * @param seq    numbers the strokes; the server acknowledges each in {@link InkStatePayload}
 * @param mapId  the map the client is drawing on, or {@link #NEW_CANVAS} for an
 *               empty map that has no drawing yet
 * @param shape    whether the stroke must be drawn whole or not at all (lines,
 *                 boxes, fills); pen strokes are drawn until the ink runs out
 * @param strength how watered down the ink is, {@link Ink#FULL} to {@link Ink#FAINTEST}
 * @param pixels the pixels to apply the material to, row by row
 */
public record InkStrokePayload(int seq, int mapId, boolean shape, int strength, BitSet pixels) implements CustomPacketPayload {
	public static final int NEW_CANVAS = -1;
	private static final int MASK_BYTES = Ink.PIXELS / 8;

	public static final Type<InkStrokePayload> TYPE = new Type<>(MapPlus.id("ink_stroke"));
	public static final StreamCodec<FriendlyByteBuf, InkStrokePayload> CODEC = StreamCodec.ofMember(
			InkStrokePayload::write,
			InkStrokePayload::read
	);

	/** A stroke at full strength. */
	public InkStrokePayload(int seq, int mapId, boolean shape, BitSet pixels) {
		this(seq, mapId, shape, Ink.FULL, pixels);
	}

	private static InkStrokePayload read(FriendlyByteBuf buf) {
		int seq = buf.readVarInt();
		int mapId = buf.readVarInt();
		boolean shape = buf.readBoolean();
		int strength = Ink.clampStrength(buf.readUnsignedByte());
		BitSet pixels;
		if (buf.readBoolean()) {
			byte[] mask = new byte[MASK_BYTES];
			buf.readBytes(mask);
			pixels = BitSet.valueOf(mask);
		} else {
			int count = buf.readVarInt();
			if (count < 0 || count > Ink.PIXELS) {
				throw new DecoderException("Too many pixels: " + count);
			}
			pixels = new BitSet(Ink.PIXELS);
			for (int i = 0; i < count; i++) {
				pixels.set(buf.readUnsignedShort() % Ink.PIXELS);
			}
		}
		return new InkStrokePayload(seq, mapId, shape, strength, pixels);
	}

	// Small strokes go as a list of pixels, large ones as a bit per pixel.
	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(seq);
		buf.writeVarInt(mapId);
		buf.writeBoolean(shape);
		buf.writeByte(strength);
		BitSet clipped = pixels.get(0, Ink.PIXELS);
		int count = clipped.cardinality();
		boolean mask = count * 2 >= MASK_BYTES;
		buf.writeBoolean(mask);
		if (mask) {
			buf.writeBytes(Arrays.copyOf(clipped.toByteArray(), MASK_BYTES));
		} else {
			buf.writeVarInt(count);
			for (int i = clipped.nextSetBit(0); i >= 0; i = clipped.nextSetBit(i + 1)) {
				buf.writeShort(i);
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
