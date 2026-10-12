package com.assilf.mapplus.ink;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;

/**
 * Using a filled map on a water cauldron washes its ink off, like washing dye
 * off leather armor, and uses up one level of water.
 */
public final class CauldronWashing {
	private CauldronWashing() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!(level instanceof ServerLevel serverLevel) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.FILLED_MAP)) {
				return InteractionResult.PASS;
			}
			BlockState state = level.getBlockState(hitResult.getBlockPos());
			if (!state.is(Blocks.WATER_CAULDRON)) {
				return InteractionResult.PASS;
			}
			MapId mapId = stack.get(DataComponents.MAP_ID);
			if (mapId == null || !InkManager.wash(serverLevel.getServer(), mapId)) {
				return InteractionResult.PASS;
			}

			LayeredCauldronBlock.lowerFillLevel(state, level, hitResult.getBlockPos());
			level.playSound(null, hitResult.getBlockPos(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6F, 1.2F);
			return InteractionResult.SUCCESS;
		});
	}
}
