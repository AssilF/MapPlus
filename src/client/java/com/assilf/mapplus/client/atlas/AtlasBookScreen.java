package com.assilf.mapplus.client.atlas;

import net.minecraft.world.InteractionHand;

/** A vanilla book screen that may be showing an atlas. Implemented on the book screens. */
public interface AtlasBookScreen {
	/** The hand holding the atlas on screen, or null if it isn't an atlas the player holds. */
	InteractionHand mapplus$atlasHand();

	/** The page the book is open on. */
	int mapplus$atlasPage();

	/**
	 * The page turning to the next or previous page lands on, stepping over map
	 * pages: -1 if there is none, or -2 if the book isn't an atlas.
	 */
	int mapplus$turnTarget(boolean forward);
}
