package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.InkSupply;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server tells the client the current state of a map being inked.
 *
 * @param open   whether the client may keep editing (false closes the ink screen)
 * @param colors the map's own colors, without ink
 * @param ink    the ink layer
 */
public record InkStatePayload(int mapId, boolean open, boolean sealed, byte[] colors, byte[] ink, InkSupply supply)
		implements CustomPacketPayload {
	public static final Type<InkStatePayload> TYPE = new Type<>(MapPlus.id("ink_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, InkStatePayload> CODEC = StreamCodec.ofMember(
			InkStatePayload::write,
			InkStatePayload::read
	);

	public static InkStatePayload closed(int mapId) {
		return new InkStatePayload(mapId, false, false, new byte[Ink.PIXELS], new byte[Ink.PIXELS], InkSupply.EMPTY);
	}

	private static InkStatePayload read(RegistryFriendlyByteBuf buf) {
		int mapId = buf.readVarInt();
		boolean open = buf.readBoolean();
		boolean sealed = buf.readBoolean();
		byte[] colors = fit(buf.readByteArray(Ink.PIXELS));
		byte[] ink = fit(buf.readByteArray(Ink.PIXELS));
		int[] dyes = new int[InkSupply.DYES];
		for (int i = 0; i < dyes.length; i++) {
			dyes[i] = buf.readVarInt();
		}
		int glow = buf.readVarInt();
		int water = buf.readVarInt();
		return new InkStatePayload(mapId, open, sealed, colors, ink, new InkSupply(dyes, glow, water));
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(mapId);
		buf.writeBoolean(open);
		buf.writeBoolean(sealed);
		buf.writeByteArray(colors);
		buf.writeByteArray(ink);
		for (int amount : supply.dyes()) {
			buf.writeVarInt(amount);
		}
		buf.writeVarInt(supply.glow());
		buf.writeVarInt(supply.water());
	}

	private static byte[] fit(byte[] bytes) {
		return bytes.length == Ink.PIXELS ? bytes : java.util.Arrays.copyOf(bytes, Ink.PIXELS);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
