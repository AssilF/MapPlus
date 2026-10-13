package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.MapPlusClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Strokes still being drawn reach the server before the table closes. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "onClose", at = @At("HEAD"))
	private void mapplus$flushDrawing(CallbackInfo ci) {
		MapPlusClient.beforeClose((Screen) (Object) this);
	}
}
