package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.scope.ScopeZoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrolling while looking through a spyglass zooms it, instead of changing the selected hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onScroll", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"), cancellable = true)
	private void mapplus$zoomSpyglass(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (minecraft.player != null && minecraft.player.isScoping() && yOffset != 0) {
			ScopeZoom.scroll(yOffset);
			ci.cancel();
		}
	}
}
