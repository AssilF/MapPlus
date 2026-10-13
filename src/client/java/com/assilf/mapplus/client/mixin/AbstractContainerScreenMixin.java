package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.MapPlusClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The drawing desk in vanilla's container screen: strokes still being drawn
 * reach the server before the table closes, and a tool carried over the map
 * is held by its tip rather than its middle, so it doesn't hide the drawing.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "onClose", at = @At("HEAD"))
	private void mapplus$flushDrawing(CallbackInfo ci) {
		MapPlusClient.beforeClose((Screen) (Object) this);
	}

	@WrapOperation(method = "extractCarriedItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractFloatingItem(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
	private void mapplus$holdToolByItsTip(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, ItemStack stack, int x, int y,
			String text, Operation<Void> original) {
		// Vanilla centers the item on the mouse; over the map, its bottom left corner is the point drawn on.
		boolean tip = MapPlusClient.holdsToolOverMap(screen, x + 8, y + 8);
		original.call(screen, graphics, stack, tip ? x + 7 : x, tip ? y - 7 : y, text);
	}
}
