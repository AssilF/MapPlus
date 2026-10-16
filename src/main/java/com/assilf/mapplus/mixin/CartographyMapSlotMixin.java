package com.assilf.mapplus.mixin;

import com.assilf.mapplus.ink.DeskSlot;
import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.ink.DrawingDesk;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * On a drawing desk the first slot also takes empty maps, as blank canvases.
 */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$3")
public abstract class CartographyMapSlotMixin implements DeskSlot {
	@Unique
	private DrawingDesk mapplus$desk;

	@Override
	public void mapplus$setDesk(DrawingDesk desk) {
		mapplus$desk = desk;
	}

	@ModifyReturnValue(method = "mayPlace", at = @At("RETURN"))
	private boolean mapplus$acceptCanvas(boolean original, ItemStack stack) {
		return original || (mapplus$desk != null && mapplus$desk.mapplus$isDesk() && (DrawingDesk.isBlankCanvas(stack) || Atlas.canCut(stack)));
	}
}
