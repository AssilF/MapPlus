package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.ink.Ink;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Server sends a player with the mod the watered down ink on a map, which
 * isn't in the map colors they're sent: their game blends it over the map
 * itself, so the map shows through it. Sent with the map when it changes.
 *
 * @param tints the watered down ink pixels, and no ink everywhere else
 */
public record MapTintsPayload(int mapId, byte[] tints) implements CustomPacketPayload {
	public static final Type<MapTintsPayload> TYPE = new Type<>(MapPlus.id("map_tints"));
	public static final StreamCodec<FriendlyByteBuf, MapTintsPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.mapId);
				buf.writeByteArray(deflate(payload.tints));
			},
			buf -> new MapTintsPayload(buf.readVarInt(), inflate(buf.readByteArray(Ink.PIXELS + 1024)))
	);

	// Mostly no ink, so it packs down to very little.
	private static byte[] deflate(byte[] pixels) {
		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		deflater.setInput(pixels, 0, Ink.PIXELS);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] chunk = new byte[1024];
		while (!deflater.finished()) {
			out.write(chunk, 0, deflater.deflate(chunk));
		}
		deflater.end();
		return out.toByteArray();
	}

	private static byte[] inflate(byte[] packed) {
		Inflater inflater = new Inflater();
		inflater.setInput(packed);
		byte[] pixels = new byte[Ink.PIXELS];
		try {
			int length = 0;
			while (length < Ink.PIXELS && !inflater.finished()) {
				int read = inflater.inflate(pixels, length, Ink.PIXELS - length);
				if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
					break;
				}
				length += read;
			}
		} catch (DataFormatException e) {
			throw new DecoderException("Bad map tints", e);
		} finally {
			inflater.end();
		}
		return pixels;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
