package com.assilf.mapplus.mixin;

import com.assilf.mapplus.ink.InkManager;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Locking or zooming out a map at the cartography table makes a new map. Its
 * ink and seal come along: copied as they are onto a locked map, shrunk into
 * place on a zoomed-out one.
 */
@Mixin(MapItem.class)
public abstract class MapItemMixin {
	@Inject(method = "onCraftedPostProcess", at = @At("HEAD"))
	private void mapplus$rememberMap(ItemStack stack, Level level, CallbackInfo ci,
			@Share("from") LocalRef<MapId> from, @Share("processing") LocalRef<MapPostProcessing> processing) {
		from.set(stack.get(DataComponents.MAP_ID));
		processing.set(stack.get(DataComponents.MAP_POST_PROCESSING));
	}

	@Inject(method = "onCraftedPostProcess", at = @At("RETURN"))
	private void mapplus$carryInk(ItemStack stack, Level level, CallbackInfo ci,
			@Share("from") LocalRef<MapId> from, @Share("processing") LocalRef<MapPostProcessing> processing) {
		MapId to = stack.get(DataComponents.MAP_ID);
		if (level instanceof ServerLevel serverLevel && processing.get() != null && from.get() != null && to != null && !to.equals(from.get())) {
			InkManager.carryInk(serverLevel, from.get(), to);
		}
	}
}
