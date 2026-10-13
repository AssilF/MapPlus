package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

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
 * @param sheet     the maps on the table's sheet, row by row: the table's map in the middle and
 *                  the maps the player carries around it, or -1 (see {@code DrawingDesk.sheet})
 * @param sealedSheet which of the sheet's maps are sealed, a bit each
 */
public record InkStatePayload(int ack, int canvasId, boolean sealed, int charge, int perItem, boolean free, boolean loom,
		boolean scrapable, List<Integer> sheet, int sealedSheet) implements CustomPacketPayload {
	public static final List<Integer> NO_SHEET = List.of(-1, -1, -1, -1, -1, -1, -1, -1, -1);
	public static final InkStatePayload NONE = new InkStatePayload(0, -1, false, 0, 1, false, false, true, NO_SHEET, 0);

	public static final Type<InkStatePayload> TYPE = new Type<>(MapPlus.id("ink_state"));
	public static final StreamCodec<FriendlyByteBuf, InkStatePayload> CODEC = StreamCodec.of(
			(buf, state) -> {
				buf.writeVarInt(state.ack);
				buf.writeVarInt(state.canvasId);
				buf.writeVarInt(state.charge);
				buf.writeVarInt(state.perItem);
				buf.writeByte((state.sealed ? 1 : 0) | (state.free ? 2 : 0) | (state.loom ? 4 : 0) | (state.scrapable ? 8 : 0));
				for (int i = 0; i < NO_SHEET.size(); i++) {
					buf.writeVarInt(i < state.sheet.size() ? state.sheet.get(i) + 1 : 0);
				}
				buf.writeVarInt(state.sealedSheet);
			},
			buf -> {
				int ack = buf.readVarInt();
				int canvasId = buf.readVarInt();
				int charge = buf.readVarInt();
				int perItem = buf.readVarInt();
				int flags = buf.readUnsignedByte();
				Integer[] sheet = new Integer[NO_SHEET.size()];
				for (int i = 0; i < sheet.length; i++) {
					sheet[i] = buf.readVarInt() - 1;
				}
				int sealedSheet = buf.readVarInt();
				return new InkStatePayload(ack, canvasId, (flags & 1) != 0, charge, perItem, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0,
						List.of(sheet), sealedSheet);
			}
	);

	/** The same state, forgetting the table it was about. */
	public InkStatePayload forNewTable() {
		return new InkStatePayload(ack, -1, false, 0, 1, false, false, true, NO_SHEET, 0);
	}

	/** The map at a place on the sheet, or -1. */
	public int sheetMap(int index) {
		return index >= 0 && index < sheet.size() ? sheet.get(index) : -1;
	}

	/** Whether the sheet has any map beside the table's own. */
	public boolean hasNeighbors() {
		for (int i = 0; i < sheet.size(); i++) {
			if (i != sheet.size() / 2 && sheet.get(i) >= 0) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
