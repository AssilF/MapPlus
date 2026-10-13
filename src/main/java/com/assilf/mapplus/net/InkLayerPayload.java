package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.ink.Ink;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server sends a player at a drawing desk the ink on the map in front of them,
 * so the fill tool can tell ink from the map under it. Sent when the map on the
 * table changes, and when its ink does.
 */
public record InkLayerPayload(int mapId, byte[] pixels) implements CustomPacketPayload {
	public static final Type<InkLayerPayload> TYPE = new Type<>(MapPlus.id("ink_layer"));
	public static final StreamCodec<FriendlyByteBuf, InkLayerPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.mapId);
				buf.writeBytes(payload.pixels, 0, Ink.PIXELS);
			},
			buf -> {
				int mapId = buf.readVarInt();
				byte[] pixels = new byte[Ink.PIXELS];
				buf.readBytes(pixels);
				return new InkLayerPayload(mapId, pixels);
			}
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
