package com.assilf.mapplus.client.marks;

import com.assilf.mapplus.ink.Mark;

import java.util.List;

/** Carries a map's marks on its render state, from extraction to drawing. Implemented on {@code MapRenderState}. */
public interface MarkHolder {
	List<Mark> mapplus$marks();

	void mapplus$setMarks(List<Mark> marks);
}
