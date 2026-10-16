package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.marks.MarkHolder;
import com.assilf.mapplus.client.marks.MarkRenderer;
import com.assilf.mapplus.ink.Mark;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.MapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws a map's marks when the map is shown in a screen: the drawing desk and atlases. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	@Inject(method = "map", at = @At("TAIL"))
	private void mapplus$drawMarks(MapRenderState state, CallbackInfo ci) {
		GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) (Object) this;
		for (Mark mark : ((MarkHolder) state).mapplus$marks()) {
			MarkRenderer.draw(graphics, mark);
		}
	}
}
