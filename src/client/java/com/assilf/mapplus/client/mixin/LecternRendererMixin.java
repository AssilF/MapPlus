package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.atlas.LecternBookState;
import com.assilf.mapplus.client.atlas.LecternView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.LecternRenderer;
import net.minecraft.client.renderer.blockentity.state.LecternRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A lectern's book lies open at its page for players near enough to read it, when the server says what it is. */
@Mixin(LecternRenderer.class)
public abstract class LecternRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/LecternBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/LecternRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
			at = @At("TAIL"))
	private void mapplus$extractBook(LecternBlockEntity lectern, LecternRenderState state, float partialTick, Vec3 camera,
			ModelFeatureRenderer.CrumblingOverlay breaking, CallbackInfo info) {
		LecternView.Shown shown = state.hasBook ? LecternView.at(lectern.getBlockPos()) : null;
		((LecternBookState) state).mapplus$set(shown, lectern.getBlockPos(), lectern.getBlockState().getValue(LecternBlock.FACING));
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/LecternRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("HEAD"), cancellable = true)
	private void mapplus$openBook(LecternRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo info) {
		LecternBookState book = (LecternBookState) state;
		if (state.hasBook && book.mapplus$shown() != null && book.mapplus$facing() != null
				&& LecternView.submit(book.mapplus$shown(), book.mapplus$pos(), book.mapplus$facing(), camera.pos, pose, collector, state.lightCoords)) {
			info.cancel();
		}
	}
}
