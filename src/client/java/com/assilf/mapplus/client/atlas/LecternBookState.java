package com.assilf.mapplus.client.atlas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** What a lectern's render state carries for its open book. Implemented on the lectern's render state. */
public interface LecternBookState {
	void mapplus$set(LecternView.Shown shown, BlockPos pos, Direction facing);

	LecternView.Shown mapplus$shown();

	BlockPos mapplus$pos();

	Direction mapplus$facing();
}
