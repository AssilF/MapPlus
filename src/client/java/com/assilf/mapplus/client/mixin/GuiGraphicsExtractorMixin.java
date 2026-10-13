package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.marks.LabelHolder;
import com.assilf.mapplus.client.marks.Labels;
import com.assilf.mapplus.ink.Mark;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.MapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws a map's labels when the map is shown in a screen: the drawing desk and atlases. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	@Inject(method = "map", at = @At("TAIL"))
	private void mapplus$drawLabels(MapRenderState state, CallbackInfo ci) {
		GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) (Object) this;
		for (Mark label : ((LabelHolder) state).mapplus$labels()) {
			Labels.draw(graphics, label.text(), label.x(), label.y(), label.size(), Labels.color(label.color()));
		}
	}
}
