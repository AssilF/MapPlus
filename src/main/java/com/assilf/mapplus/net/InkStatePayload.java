package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server tells a player at a cartography table what their drawing desk needs
 * to show. Sent when something in it changes.
 *
 * @param ack       the last stroke or mark edit the server has applied; it's in the map now
 * @param canvasId  the map holding the drawing on the empty map in the first slot, or -1
 * @param sealed    whether the map in the first slot is sealed against drawing
 * @param charge    ink of the second slot's material the player has loaded but not used
 * @param perItem   pixels one item of that material covers
 * @param free      whether drawing costs nothing (creative)
 * @param loom      whether labels, pins and borders can be used here (a loom stands next to the table)
 * @param scrapable whether this player may scrape the seal off
 */
public record InkStatePayload(int ack, int canvasId, boolean sealed, int charge, int perItem, boolean free, boolean loom,
		boolean scrapable) implements CustomPacketPayload {
	public static final InkStatePayload NONE = new InkStatePayload(0, -1, false, 0, 1, false, false, true);

	public static final Type<InkStatePayload> TYPE = new Type<>(MapPlus.id("ink_state"));
	public static final StreamCodec<FriendlyByteBuf, InkStatePayload> CODEC = StreamCodec.of(
			(buf, state) -> {
				buf.writeVarInt(state.ack);
				buf.writeVarInt(state.canvasId);
				buf.writeVarInt(state.charge);
				buf.writeVarInt(state.perItem);
				buf.writeByte((state.sealed ? 1 : 0) | (state.free ? 2 : 0) | (state.loom ? 4 : 0) | (state.scrapable ? 8 : 0));
			},
			buf -> {
				int ack = buf.readVarInt();
				int canvasId = buf.readVarInt();
				int charge = buf.readVarInt();
				int perItem = buf.readVarInt();
				int flags = buf.readUnsignedByte();
				return new InkStatePayload(ack, canvasId, (flags & 1) != 0, charge, perItem, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0);
			}
	);

	/** The same state, forgetting the table it was about. */
	public InkStatePayload forNewTable() {
		return new InkStatePayload(ack, -1, false, 0, 1, false, false, true);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
