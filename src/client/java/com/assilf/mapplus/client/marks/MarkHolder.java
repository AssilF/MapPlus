package com.assilf.mapplus.client.marks;

import com.assilf.mapplus.ink.Mark;

import java.util.List;

/** Carries a map's marks on its render state, from extraction to drawing. Implemented on {@code MapRenderState}. */
public interface MarkHolder {
	List<Mark> mapplus$marks();

	/** The map drawn, or -1 if it isn't known. */
	int mapplus$mapId();

	void mapplus$setMarks(int mapId, List<Mark> marks);
}
