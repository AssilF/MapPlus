package com.assilf.mapplus.mixin;

import com.assilf.mapplus.ink.FramePainting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Using a framed map with a pen in one hand and ink in the other paints it, instead of turning it. */
@Mixin(ItemFrame.class)
public abstract class ItemFrameMixin {
	@Shadow
	private boolean fixed;

	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void mapplus$paint(Player player, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
		ItemFrame frame = (ItemFrame) (Object) this;
		if (FramePainting.paints(player, frame, fixed)) {
			if (player.level() instanceof ServerLevel level) {
				FramePainting.paint(level, player, frame, location);
			}
			cir.setReturnValue(InteractionResult.SUCCESS);
		}
	}
}
