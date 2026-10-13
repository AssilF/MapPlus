package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.ink.Mark;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server tells a player with the mod the labels and pins on a map they see.
 * Sent with the map's own updates, whenever the marks change. Players without
 * the mod get them as vanilla banner markers instead.
 */
public record MapMarksPayload(int mapId, List<Mark> marks) implements CustomPacketPayload {
	private static final int MAX_MARKS = 1024;

	public static final Type<MapMarksPayload> TYPE = new Type<>(MapPlus.id("map_marks"));
	public static final StreamCodec<FriendlyByteBuf, MapMarksPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MapMarksPayload::mapId,
			Mark.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MARKS)), MapMarksPayload::marks,
			MapMarksPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
