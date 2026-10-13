package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.marks.LabelHolder;
import com.assilf.mapplus.ink.Mark;
import net.minecraft.client.renderer.state.MapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(MapRenderState.class)
public abstract class MapRenderStateMixin implements LabelHolder {
	@Unique
	private List<Mark> mapplus$labels = List.of();

	@Override
	public List<Mark> mapplus$labels() {
		return mapplus$labels;
	}

	@Override
	public void mapplus$setLabels(List<Mark> labels) {
		mapplus$labels = labels;
	}
}
