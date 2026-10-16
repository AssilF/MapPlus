package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.ink.ClientInk;
import com.assilf.mapplus.client.marks.MarkHolder;
import com.assilf.mapplus.client.marks.MarkRenderer;
import com.assilf.mapplus.ink.Mark;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a map's marks wherever the map is drawn in the world: held, and in
 * item frames. Maps in screens are drawn by {@link GuiGraphicsExtractorMixin}.
 */
@Mixin(MapRenderer.class)
public abstract class MapRendererMixin {
	@Unique
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Just over the map, under its markers. */
	@Unique
	private static final float GLOW_Z = -0.012F;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void mapplus$extractMarks(MapId mapId, MapItemSavedData data, MapRenderState state, CallbackInfo ci) {
		((MarkHolder) state).mapplus$setMarks(mapId.id(), MarkRenderer.shown(mapId));
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void mapplus$renderMarks(MapRenderState state, PoseStack pose, SubmitNodeCollector collector, boolean inFrame, int light,
			CallbackInfo ci) {
		MarkHolder holder = (MarkHolder) state;
		// Glow ink shines at full brightness over the map, whatever the light where it hangs.
		Identifier glow = ClientInk.glowTexture(holder.mapplus$mapId());
		if (glow != null) {
			collector.order(1).submitCustomGeometry(pose, RenderTypes.text(glow), (at, vertices) -> {
				vertices.addVertex(at, 0.0F, 128.0F, GLOW_Z).setColor(-1).setUv(0.0F, 1.0F).setLight(FULL_BRIGHT);
				vertices.addVertex(at, 128.0F, 128.0F, GLOW_Z).setColor(-1).setUv(1.0F, 1.0F).setLight(FULL_BRIGHT);
				vertices.addVertex(at, 128.0F, 0.0F, GLOW_Z).setColor(-1).setUv(1.0F, 0.0F).setLight(FULL_BRIGHT);
				vertices.addVertex(at, 0.0F, 0.0F, GLOW_Z).setColor(-1).setUv(0.0F, 0.0F).setLight(FULL_BRIGHT);
			});
		}
		for (Mark mark : holder.mapplus$marks()) {
			MarkRenderer.submit(mark, pose, collector, mark.glow() ? FULL_BRIGHT : light);
		}
	}
}
