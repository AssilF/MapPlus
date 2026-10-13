package com.assilf.mapplus.mixin;

import com.assilf.mapplus.ink.DeskSlot;
import com.assilf.mapplus.ink.DrawingDesk;
import com.assilf.mapplus.ink.InkMaterial;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * On a drawing desk the second slot also takes what draws (dyes, ink sacs, glow
 * ink sacs, water bottles), what seals (honeycomb, axes) and what binds (book
 * and quill).
 */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$4")
public abstract class CartographyAdditionalSlotMixin implements DeskSlot {
	@Unique
	private DrawingDesk mapplus$desk;

	@Override
	public void mapplus$setDesk(DrawingDesk desk) {
		mapplus$desk = desk;
	}

	@ModifyReturnValue(method = "mayPlace", at = @At("RETURN"))
	private boolean mapplus$acceptDeskItems(boolean original, ItemStack stack) {
		if (original || mapplus$desk == null || !mapplus$desk.mapplus$isDesk()) {
			return original;
		}
		return stack.is(Items.WRITABLE_BOOK) || InkMaterial.fitsTable(stack);
	}
}
