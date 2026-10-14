package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.atlas.OpenBook;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A book in hand is held open as a map is, in both hands when the other hand
 * is free: the spread it was last read at, with its writing, sketches and maps.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;has(Lnet/minecraft/core/component/DataComponentType;)Z"))
	private boolean mapplus$heldLikeAMap(ItemStack stack, DataComponentType<?> type, Operation<Boolean> original) {
		return original.call(stack, type) || type == DataComponents.MAP_ID && OpenBook.isBook(stack);
	}

	@Inject(method = "renderMap", at = @At("HEAD"), cancellable = true)
	private void mapplus$openBook(PoseStack pose, SubmitNodeCollector collector, int light, ItemStack stack, boolean mainHand,
			FirstPersonHandsAndItemsRenderState state, CallbackInfo info) {
		if (!OpenBook.isBook(stack)) {
			return;
		}
		// As a map is held, then the spread fitted over where the map would be.
		pose.rotateDegrees(Axis.YP, 180.0F);
		pose.rotateDegrees(Axis.ZP, 180.0F);
		pose.scale(0.38F, 0.38F, 0.38F);
		pose.translate(-0.5F, -0.5F, 0.0F);
		pose.scale(0.0078125F, 0.0078125F, 0.0078125F);
		float scale = OpenBook.HELD_WIDTH / (OpenBook.MAX_X - OpenBook.MIN_X);
		pose.translate(64.0F - (OpenBook.MIN_X + OpenBook.MAX_X) / 2 * scale, 64.0F - (OpenBook.MIN_Y + OpenBook.MAX_Y) / 2 * scale, 0.0F);
		pose.scale(scale, scale, scale);
		OpenBook.submit(pose, collector, light, stack, OpenBook.page(stack));
		info.cancel();
	}
}
