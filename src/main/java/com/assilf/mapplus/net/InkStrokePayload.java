package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.ink.Ink;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client draws or erases a set of pixels.
 *
 * @param dye  dye id to draw with, or {@link #ERASE}
 * @param mask one bit per map pixel, row by row
 */
public record InkStrokePayload(int mapId, int dye, boolean glow, byte[] mask) implements CustomPacketPayload {
	public static final int ERASE = -1;
	public static final int MASK_BYTES = Ink.PIXELS / 8;

	public static final Type<InkStrokePayload> TYPE = new Type<>(MapPlus.id("ink_stroke"));
	public static final StreamCodec<RegistryFriendlyByteBuf, InkStrokePayload> CODEC = StreamCodec.ofMember(
			InkStrokePayload::write,
			InkStrokePayload::read
	);

	private static InkStrokePayload read(RegistryFriendlyByteBuf buf) {
		int mapId = buf.readVarInt();
		int dye = buf.readVarInt();
		boolean glow = buf.readBoolean();
		byte[] mask = buf.readByteArray(MASK_BYTES);
		if (mask.length != MASK_BYTES) {
			mask = java.util.Arrays.copyOf(mask, MASK_BYTES);
		}
		return new InkStrokePayload(mapId, dye, glow, mask);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(mapId);
		buf.writeVarInt(dye);
		buf.writeBoolean(glow);
		buf.writeByteArray(mask);
	}

	public boolean isSet(int index) {
		return isSet(mask, index);
	}

	public static boolean isSet(byte[] mask, int index) {
		return (mask[index >> 3] & (1 << (index & 7))) != 0;
	}

	public static void set(byte[] mask, int index) {
		mask[index >> 3] |= (byte) (1 << (index & 7));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
