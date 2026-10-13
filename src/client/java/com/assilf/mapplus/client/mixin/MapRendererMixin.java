package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.marks.LabelHolder;
import com.assilf.mapplus.client.marks.Labels;
import com.assilf.mapplus.ink.Mark;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a map's labels wherever the map is drawn in the world: held, and in
 * item frames. Maps in screens are drawn by {@link GuiGraphicsExtractorMixin}.
 */
@Mixin(MapRenderer.class)
public abstract class MapRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void mapplus$extractLabels(MapId mapId, MapItemSavedData data, MapRenderState state, CallbackInfo ci) {
		((LabelHolder) state).mapplus$setLabels(Labels.shown(mapId));
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void mapplus$renderLabels(MapRenderState state, PoseStack pose, SubmitNodeCollector collector, boolean inFrame, int light,
			CallbackInfo ci) {
		Font font = Minecraft.getInstance().font;
		for (Mark label : ((LabelHolder) state).mapplus$labels()) {
			FormattedCharSequence text = Component.literal(label.text()).getVisualOrderText();
			Labels.Fit fit = Labels.fit(label.text(), label.size(), label.x(), label.y());
			pose.pushPose();
			pose.translate(fit.x(), fit.y(), -0.025F);
			pose.scale(fit.scale(), fit.scale(), -1.0F);
			pose.translate(0.0F, 0.0F, 0.1F);
			collector.order(1).submitText(pose, -font.width(text) / 2.0F, -4.0F, text, false, Font.DisplayMode.NORMAL, light,
					Labels.color(label.color()), 0, 0);
			pose.popPose();
		}
	}
}
