package com.assilf.mapplus.client.marks;

import com.assilf.mapplus.ink.Mark;

import java.util.List;

/** Carries a map's labels on its render state, from extraction to drawing. Implemented on {@code MapRenderState}. */
public interface LabelHolder {
	List<Mark> mapplus$labels();

	void mapplus$setLabels(List<Mark> labels);
}
