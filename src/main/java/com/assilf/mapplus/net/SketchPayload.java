package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.atlas.Sketch;
import com.assilf.mapplus.ink.Ink;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

import java.util.BitSet;

/**
 * Client drew a stroke on a page of the book and quill it holds, with the ink
 * in its other hand, or erased one.
 *
 * @param offhand  whether the book is in the off hand
 * @param page     the page the stroke is on
 * @param strength how watered down the ink is, {@link Ink#FULL} to {@link Ink#FAINTEST}
 * @param erase    whether the stroke erases the sketch instead
 * @param pixels   the pixels of the page's sketch the stroke covers, row by row
 */
public record SketchPayload(boolean offhand, int page, int strength, boolean erase, BitSet pixels) implements CustomPacketPayload {
	public static final Type<SketchPayload> TYPE = new Type<>(MapPlus.id("sketch"));
	public static final StreamCodec<FriendlyByteBuf, SketchPayload> CODEC = StreamCodec.ofMember(SketchPayload::write, SketchPayload::read);

	private static SketchPayload read(FriendlyByteBuf buf) {
		boolean offhand = buf.readBoolean();
		int page = buf.readVarInt();
		int strength = Ink.clampStrength(buf.readUnsignedByte());
		boolean erase = buf.readBoolean();
		int count = buf.readVarInt();
		if (count < 0 || count > Sketch.PIXELS) {
			throw new DecoderException("Too many pixels: " + count);
		}
		BitSet pixels = new BitSet(Sketch.PIXELS);
		for (int i = 0; i < count; i++) {
			pixels.set(buf.readUnsignedShort() % Sketch.PIXELS);
		}
		return new SketchPayload(offhand, page, strength, erase, pixels);
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeBoolean(offhand);
		buf.writeVarInt(page);
		buf.writeByte(strength);
		buf.writeBoolean(erase);
		BitSet clipped = pixels.get(0, Sketch.PIXELS);
		buf.writeVarInt(clipped.cardinality());
		for (int i = clipped.nextSetBit(0); i >= 0; i = clipped.nextSetBit(i + 1)) {
			buf.writeShort(i);
		}
	}

	public InteractionHand hand() {
		return offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
