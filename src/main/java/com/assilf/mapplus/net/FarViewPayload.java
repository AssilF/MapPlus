package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server sends a player with the mod, looking through a spyglass, the land
 * past their render distance as a map would show it: for each chunk, the
 * height and map color of every 2×2 blocks.
 */
public record FarViewPayload(List<Patch> patches) implements CustomPacketPayload {
	public static final Type<FarViewPayload> TYPE = new Type<>(MapPlus.id("far_view"));
	public static final StreamCodec<FriendlyByteBuf, FarViewPayload> CODEC = StreamCodec.ofMember(FarViewPayload::write, FarViewPayload::read);

	/** Blocks across a cell of a patch. */
	public static final int STEP = 2;
	/** Cells across a patch, which is a chunk. */
	public static final int SIDE = 16 / STEP;
	public static final int CELLS = SIDE * SIDE;
	/** The most patches one payload carries. */
	public static final int MOST = 64;

	/**
	 * A chunk's land, cell by cell, row by row from the north-west.
	 *
	 * @param heights the top of each cell's land
	 * @param colors  each cell's map color, before shading
	 */
	public record Patch(int chunkX, int chunkZ, short[] heights, byte[] colors) {
	}

	private static FarViewPayload read(FriendlyByteBuf buf) {
		int count = buf.readVarInt();
		if (count < 0 || count > MOST) {
			throw new DecoderException("Too many patches: " + count);
		}
		List<Patch> patches = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int x = buf.readVarInt();
			int z = buf.readVarInt();
			short[] heights = new short[CELLS];
			byte[] colors = new byte[CELLS];
			for (int c = 0; c < CELLS; c++) {
				heights[c] = buf.readShort();
			}
			buf.readBytes(colors);
			patches.add(new Patch(x, z, heights, colors));
		}
		return new FarViewPayload(patches);
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(patches.size());
		for (Patch patch : patches) {
			buf.writeVarInt(patch.chunkX());
			buf.writeVarInt(patch.chunkZ());
			for (short height : patch.heights()) {
				buf.writeShort(height);
			}
			buf.writeBytes(patch.colors());
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
