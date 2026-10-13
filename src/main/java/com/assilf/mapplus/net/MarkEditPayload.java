package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.ink.Mark;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client places, changes, moves or removes a label or pin on the map on its
 * cartography table.
 *
 * @param seq    numbers the edit like a stroke; the server acknowledges it in {@link InkStatePayload}
 * @param mapId  the map the client is marking, or {@link InkStrokePayload#NEW_CANVAS}
 * @param action what to do
 * @param markId the mark to change, move or remove
 * @param x      where the mark goes, in map pixels
 * @param y      where the mark goes, in map pixels
 * @param size   a label's text size
 * @param text   a label's text
 */
public record MarkEditPayload(int seq, int mapId, Action action, int markId, int x, int y, int size, String text)
		implements CustomPacketPayload {
	public enum Action {
		/** Writes a new label with the sign on the cursor, in the ink of the second slot. */
		WRITE,
		/** Rewrites a label's text and size, in the ink of the second slot. */
		REWRITE,
		/** Pins the banner on the cursor. */
		PIN,
		MOVE,
		REMOVE
	}

	public static final Type<MarkEditPayload> TYPE = new Type<>(MapPlus.id("mark_edit"));
	public static final StreamCodec<FriendlyByteBuf, MarkEditPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.seq);
				buf.writeVarInt(payload.mapId);
				buf.writeByte(payload.action.ordinal());
				buf.writeVarInt(payload.markId);
				buf.writeByte(payload.x);
				buf.writeByte(payload.y);
				buf.writeByte(payload.size);
				buf.writeUtf(payload.text, Mark.MAX_TEXT * 4);
			},
			buf -> {
				int seq = buf.readVarInt();
				int mapId = buf.readVarInt();
				int action = buf.readUnsignedByte();
				Action[] actions = Action.values();
				return new MarkEditPayload(seq, mapId, actions[Math.min(action, actions.length - 1)], buf.readVarInt(),
						buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUtf(Mark.MAX_TEXT * 4));
			}
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
