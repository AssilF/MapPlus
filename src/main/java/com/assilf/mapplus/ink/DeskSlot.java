package com.assilf.mapplus.ink;

/**
 * A cartography table input slot that knows its table, so it can accept the
 * drawing desk's items only for players who have the mod.
 */
public interface DeskSlot {
	void mapplus$setDesk(DrawingDesk desk);
}
