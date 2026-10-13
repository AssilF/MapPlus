package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server tells a player with the mod where a map lies in the world: the block
 * at its middle. Vanilla never sends that to the game, which only draws maps;
 * the mod needs it to lay a map over the land it shows, as Foresight does.
 * Sent once for each map the player is sent.
 */
public record MapPlacePayload(int mapId, int centerX, int centerZ) implements CustomPacketPayload {
	public static final Type<MapPlacePayload> TYPE = new Type<>(MapPlus.id("map_place"));
	public static final StreamCodec<FriendlyByteBuf, MapPlacePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MapPlacePayload::mapId,
			ByteBufCodecs.VAR_INT, MapPlacePayload::centerX,
			ByteBufCodecs.VAR_INT, MapPlacePayload::centerZ,
			MapPlacePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
