package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.scope.ScopeZoom;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The spyglass's view narrows or widens with its zoom. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"))
	private float mapplus$zoom(float modifier) {
		return ScopeZoom.apply((AbstractClientPlayer) (Object) this, modifier);
	}
}
