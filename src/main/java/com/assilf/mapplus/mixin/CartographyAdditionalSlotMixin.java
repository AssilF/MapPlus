package com.assilf.mapplus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a book and quill (or an atlas) go in the cartography table's second slot.
 */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$4")
public abstract class CartographyAdditionalSlotMixin {
	@ModifyReturnValue(method = "mayPlace", at = @At("RETURN"))
	private boolean mapplus$acceptBooks(boolean original, ItemStack stack) {
		return original || stack.is(Items.WRITABLE_BOOK);
	}
}
