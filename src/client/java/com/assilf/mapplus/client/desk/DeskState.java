package com.assilf.mapplus.client.desk;

import com.assilf.mapplus.net.InkStatePayload;

/**
 * What the server last said about the player's drawing desk, and the numbering
 * of the strokes sent to it.
 */
public final class DeskState {
	private static InkStatePayload state = InkStatePayload.NONE;
	private static int lastSeq;

	private DeskState() {
	}

	public static InkStatePayload get() {
		return state;
	}

	public static void accept(InkStatePayload payload) {
		state = payload;
	}

	/** Forgets the last table, so a new one doesn't show its canvas or seal. */
	public static void reset() {
		state = new InkStatePayload(state.ack(), -1, false, 0, 1, false);
	}

	public static int nextSeq() {
		return ++lastSeq;
	}
}
