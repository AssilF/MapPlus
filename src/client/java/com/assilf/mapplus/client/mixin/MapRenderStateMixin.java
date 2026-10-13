package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.marks.MarkHolder;
import com.assilf.mapplus.ink.Mark;
import net.minecraft.client.renderer.state.MapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(MapRenderState.class)
public abstract class MapRenderStateMixin implements MarkHolder {
	@Unique
	private List<Mark> mapplus$marks = List.of();
	@Unique
	private int mapplus$mapId = -1;

	@Override
	public List<Mark> mapplus$marks() {
		return mapplus$marks;
	}

	@Override
	public int mapplus$mapId() {
		return mapplus$mapId;
	}

	@Override
	public void mapplus$setMarks(int mapId, List<Mark> marks) {
		mapplus$mapId = mapId;
		mapplus$marks = marks;
	}
}
