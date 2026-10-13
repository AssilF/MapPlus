package com.assilf.mapplus.client.atlas;

import net.minecraft.world.InteractionHand;

/** What sketching needs to know of an open book and quill. */
public interface SketchBookScreen {
	/** The book's sketching, or null for a screen built before it was opened. */
	default SketchPad mapplus$sketchPad() {
		return null;
	}

	/** The page being shown for editing. */
	int mapplus$currentPage();

	/** Whether a page has any text on it. */
	boolean mapplus$hasText(int page);

	/** Whether a page holds a stitched map. */
	boolean mapplus$isMapPage(int page);

	/** The hand holding the book. */
	InteractionHand mapplus$bookHand();

	/** Where the current page's writing area starts on screen. */
	int mapplus$writingX();

	int mapplus$writingY();
}
