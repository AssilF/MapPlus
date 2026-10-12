package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asks to start inking the map in its open cartography table.
 */
public record OpenInkPayload(int mapId) implements CustomPacketPayload {
	public static final Type<OpenInkPayload> TYPE = new Type<>(MapPlus.id("open_ink"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenInkPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, OpenInkPayload::mapId,
			OpenInkPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
