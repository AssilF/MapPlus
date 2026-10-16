package com.assilf.mapplus.atlas;

import com.assilf.mapplus.ink.Ink;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A sketch drawn on a page of a book and quill: ink on the page's writing area,
 * a pixel for each of its points, where a vanilla book lays out its text. A
 * sketch is a drawing, never a map: it has no map id, and lives in the book
 * itself, kept small.
 *
 * @param page which page of the book it's on
 * @param ink  its pixels, row by row, as {@link Ink} bytes
 */
public record Sketch(int page, byte[] ink) {
	/** The writing area of a book page: 114 by 14 lines of 9. */
	public static final int WIDTH = 114;
	public static final int HEIGHT = 126;
	public static final int PIXELS = WIDTH * HEIGHT;

	public static final Codec<Sketch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("page").forGetter(Sketch::page),
			Codec.BYTE_BUFFER.comapFlatMap(Sketch::inflate, Sketch::deflate).fieldOf("ink").forGetter(Sketch::ink)
	).apply(instance, Sketch::new));

	public Sketch {
		if (ink.length != PIXELS) {
			ink = Arrays.copyOf(ink, PIXELS);
		}
	}

	public static Sketch blank(int page) {
		return new Sketch(page, new byte[PIXELS]);
	}

	public byte get(int index) {
		return ink[index];
	}

	public boolean isBlank() {
		for (byte pixel : ink) {
			if (Ink.isPresent(pixel)) {
				return false;
			}
		}
		return true;
	}

	private static ByteBuffer deflate(byte[] ink) {
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		deflater.setInput(ink);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		while (!deflater.finished()) {
			out.write(buffer, 0, deflater.deflate(buffer));
		}
		deflater.end();
		return ByteBuffer.wrap(out.toByteArray());
	}

	private static DataResult<byte[]> inflate(ByteBuffer packed) {
		byte[] input = new byte[packed.remaining()];
		packed.duplicate().get(input);
		Inflater inflater = new Inflater();
		inflater.setInput(input);
		byte[] ink = new byte[PIXELS];
		try {
			int read = 0;
			while (read < PIXELS && !inflater.finished()) {
				int got = inflater.inflate(ink, read, PIXELS - read);
				if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
					break;
				}
				read += got;
			}
			return DataResult.success(ink);
		} catch (DataFormatException e) {
			return DataResult.error(() -> "Unreadable sketch: " + e.getMessage());
		} finally {
			inflater.end();
		}
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Sketch sketch && page == sketch.page && Arrays.equals(ink, sketch.ink);
	}

	@Override
	public int hashCode() {
		return 31 * page + Arrays.hashCode(ink);
	}
}
