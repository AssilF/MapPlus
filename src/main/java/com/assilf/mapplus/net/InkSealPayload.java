package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client seals the map's ink with honeycomb, or scrapes the seal off with an axe.
 */
public record InkSealPayload(int mapId, boolean seal) implements CustomPacketPayload {
	public static final Type<InkSealPayload> TYPE = new Type<>(MapPlus.id("ink_seal"));
	public static final StreamCodec<RegistryFriendlyByteBuf, InkSealPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, InkSealPayload::mapId,
			ByteBufCodecs.BOOL, InkSealPayload::seal,
			InkSealPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
