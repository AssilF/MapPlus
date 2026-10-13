package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.atlas.AtlasBookScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On a lectern the server turns the pages; an atlas asks it to jump over map
 * pages, which are shown beside their notes.
 */
@Mixin(LecternScreen.class)
public abstract class LecternScreenMixin extends BookViewScreen {
	@Inject(method = "pageForward", at = @At("HEAD"), cancellable = true)
	private void mapplus$turnForward(CallbackInfo ci) {
		mapplus$turn(true, ci);
	}

	@Inject(method = "pageBack", at = @At("HEAD"), cancellable = true)
	private void mapplus$turnBack(CallbackInfo ci) {
		mapplus$turn(false, ci);
	}

	@Unique
	private void mapplus$turn(boolean forward, CallbackInfo ci) {
		int target = ((AtlasBookScreen) this).mapplus$turnTarget(forward);
		if (target == -2) {
			return;
		}
		if (target >= 0) {
			forcePage(target);
		}
		ci.cancel();
	}
}
