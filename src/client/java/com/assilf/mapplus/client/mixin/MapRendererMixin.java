package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.marks.MarkHolder;
import com.assilf.mapplus.client.marks.MarkRenderer;
import com.assilf.mapplus.ink.Mark;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a map's marks wherever the map is drawn in the world: held, and in
 * item frames. Maps in screens are drawn by {@link GuiGraphicsExtractorMixin}.
 */
@Mixin(MapRenderer.class)
public abstract class MapRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void mapplus$extractMarks(MapId mapId, MapItemSavedData data, MapRenderState state, CallbackInfo ci) {
		((MarkHolder) state).mapplus$setMarks(MarkRenderer.shown(mapId));
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void mapplus$renderMarks(MapRenderState state, PoseStack pose, SubmitNodeCollector collector, boolean inFrame, int light,
			CallbackInfo ci) {
		for (Mark mark : ((MarkHolder) state).mapplus$marks()) {
			MarkRenderer.submit(mark, pose, collector, light);
		}
	}
}
