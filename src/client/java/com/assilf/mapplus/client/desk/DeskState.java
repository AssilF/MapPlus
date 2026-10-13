package com.assilf.mapplus.client.desk;

import com.assilf.mapplus.net.InkLayerPayload;
import com.assilf.mapplus.net.InkStatePayload;

/**
 * What the server last said about the player's drawing desk, and the numbering
 * of the strokes sent to it.
 */
public final class DeskState {
	private static InkStatePayload state = InkStatePayload.NONE;
	private static InkLayerPayload layer;
	private static int lastSeq;

	private DeskState() {
	}

	public static InkStatePayload get() {
		return state;
	}

	public static void accept(InkStatePayload payload) {
		state = payload;
	}

	public static void accept(InkLayerPayload payload) {
		layer = payload;
	}

	/** The ink on a map, as the server last sent it, or null if it hasn't arrived. */
	public static byte[] ink(int mapId) {
		return layer != null && layer.mapId() == mapId ? layer.pixels() : null;
	}

	/** Forgets the last table, so a new one doesn't show its canvas or seal. */
	public static void reset() {
		state = state.forNewTable();
		layer = null;
	}

	public static int nextSeq() {
		return ++lastSeq;
	}
}
