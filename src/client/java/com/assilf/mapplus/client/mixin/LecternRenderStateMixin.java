package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.atlas.LecternBookState;
import com.assilf.mapplus.client.atlas.LecternView;
import net.minecraft.client.renderer.blockentity.state.LecternRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries a lectern's book from the lectern to where it's drawn. */
@Mixin(LecternRenderState.class)
public abstract class LecternRenderStateMixin implements LecternBookState {
	@Unique
	private LecternView.Shown mapplus$shown;
	@Unique
	private BlockPos mapplus$pos;
	@Unique
	private Direction mapplus$facing;

	@Override
	public void mapplus$set(LecternView.Shown shown, BlockPos pos, Direction facing) {
		mapplus$shown = shown;
		mapplus$pos = pos;
		mapplus$facing = facing;
	}

	@Override
	public LecternView.Shown mapplus$shown() {
		return mapplus$shown;
	}

	@Override
	public BlockPos mapplus$pos() {
		return mapplus$pos;
	}

	@Override
	public Direction mapplus$facing() {
		return mapplus$facing;
	}
}
