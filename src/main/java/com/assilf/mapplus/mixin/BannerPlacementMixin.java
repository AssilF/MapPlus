package com.assilf.mapplus.mixin;

import com.assilf.mapplus.nation.Nations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A named banner put up in the world becomes a border post of its nation. */
@Mixin(Block.class)
public abstract class BannerPlacementMixin {
	@Inject(method = "setPlacedBy", at = @At("HEAD"))
	private void mapplus$putUpPost(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
		if ((Object) this instanceof AbstractBannerBlock banner && level instanceof ServerLevel serverLevel) {
			Nations.placed(serverLevel, pos, banner, placer, stack);
		}
	}
}
