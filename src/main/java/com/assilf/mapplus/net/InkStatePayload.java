package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server tells a player at a cartography table what their drawing desk needs
 * to show. Sent when something in it changes.
 *
 * @param ack      the last stroke the server has applied; its pixels are in the map now
 * @param canvasId the map holding the drawing on the empty map in the first slot, or -1
 * @param sealed   whether the map in the first slot is sealed against drawing
 * @param charge   ink of the second slot's material the player has loaded but not used
 * @param perItem  pixels one item of that material covers
 * @param free     whether drawing costs nothing (creative)
 */
public record InkStatePayload(int ack, int canvasId, boolean sealed, int charge, int perItem, boolean free)
		implements CustomPacketPayload {
	public static final InkStatePayload NONE = new InkStatePayload(0, -1, false, 0, 1, false);

	public static final Type<InkStatePayload> TYPE = new Type<>(MapPlus.id("ink_state"));
	public static final StreamCodec<FriendlyByteBuf, InkStatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, InkStatePayload::ack,
			ByteBufCodecs.VAR_INT, InkStatePayload::canvasId,
			ByteBufCodecs.BOOL, InkStatePayload::sealed,
			ByteBufCodecs.VAR_INT, InkStatePayload::charge,
			ByteBufCodecs.VAR_INT, InkStatePayload::perItem,
			ByteBufCodecs.BOOL, InkStatePayload::free,
			InkStatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
