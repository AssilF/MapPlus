package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client is painting a framed map: where on the frame the crosshair is now.
 * Sent every tick while use is held, so strokes are as smooth as the mouse;
 * players without the mod paint through vanilla's slower repeated use instead.
 */
public record FramePaintPayload(int frame, double x, double y, double z) implements CustomPacketPayload {
	public static final Type<FramePaintPayload> TYPE = new Type<>(MapPlus.id("frame_paint"));
	public static final StreamCodec<FriendlyByteBuf, FramePaintPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FramePaintPayload::frame,
			ByteBufCodecs.DOUBLE, FramePaintPayload::x,
			ByteBufCodecs.DOUBLE, FramePaintPayload::y,
			ByteBufCodecs.DOUBLE, FramePaintPayload::z,
			FramePaintPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
