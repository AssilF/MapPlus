package com.assilf.mapplus.client.marks;

import com.assilf.mapplus.ink.Mark;
import com.assilf.mapplus.net.MapMarksPayload;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The labels and pins on each map the player has seen, as the server last sent
 * them. Pins also arrive as the map's own markers; labels are drawn from here.
 */
public final class ClientMarks {
	private static final Map<Integer, List<Mark>> MARKS = new HashMap<>();
	private static final Map<Integer, Integer> UPDATES = new HashMap<>();
	/** A label being edited at the desk, not drawn on the map meanwhile. */
	private static int hiddenMap = -1;
	private static int hiddenMark = -1;
	private static int updates;

	private ClientMarks() {
	}

	public static void accept(MapMarksPayload payload) {
		if (payload.marks().isEmpty()) {
			MARKS.remove(payload.mapId());
		} else {
			MARKS.put(payload.mapId(), List.copyOf(payload.marks()));
		}
		UPDATES.merge(payload.mapId(), 1, Integer::sum);
		updates++;
	}

	public static List<Mark> get(MapId mapId) {
		return mapId == null ? List.of() : MARKS.getOrDefault(mapId.id(), List.of());
	}

	/**
	 * Counts the updates received for a map, so the desk knows when its edit has
	 * come back; for a map not made yet (a negative id), counts all updates.
	 */
	public static int updates(int mapId) {
		return mapId < 0 ? updates : UPDATES.getOrDefault(mapId, 0);
	}

	public static void hide(MapId mapId, int markId) {
		hiddenMap = mapId == null ? -1 : mapId.id();
		hiddenMark = markId;
	}

	public static void unhide() {
		hiddenMap = -1;
		hiddenMark = -1;
	}

	public static boolean isHidden(int mapId, Mark mark) {
		return mapId == hiddenMap && mark.id() == hiddenMark;
	}

	public static void clear() {
		MARKS.clear();
		UPDATES.clear();
		unhide();
	}
}
