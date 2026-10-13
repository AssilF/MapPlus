package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.desk.DeskWing;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The widened table window still draws only the vanilla part of its texture;
 * the drawing desk draws the rest.
 */
@Mixin(CartographyTableScreen.class)
public abstract class CartographyTableScreenMixin {
	@ModifyArg(method = "extractBackground", index = 6, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private int mapplus$vanillaWidth(int width) {
		return Math.min(width, DeskWing.VANILLA_WIDTH);
	}
}
