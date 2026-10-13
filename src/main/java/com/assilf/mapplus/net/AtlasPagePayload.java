package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/**
 * Client closed the atlas it holds on a page. If that page is a map, the
 * atlas explores with that map while held, as if the map itself were held.
 */
public record AtlasPagePayload(boolean offhand, int page) implements CustomPacketPayload {
	public static final Type<AtlasPagePayload> TYPE = new Type<>(MapPlus.id("atlas_page"));
	public static final StreamCodec<FriendlyByteBuf, AtlasPagePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, AtlasPagePayload::offhand,
			ByteBufCodecs.VAR_INT, AtlasPagePayload::page,
			AtlasPagePayload::new
	);

	public InteractionHand hand() {
		return offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
