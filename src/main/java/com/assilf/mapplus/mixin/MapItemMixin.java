package com.assilf.mapplus.mixin;

import com.assilf.mapplus.ink.InkManager;
import com.assilf.mapplus.survey.CaveMaps;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Locking or zooming out a map at the cartography table makes a new map. Its
 * ink and seal come along: copied as they are onto a locked map, shrunk into
 * place on a zoomed-out one. Cave maps fill in their own level instead of the
 * surface.
 */
@Mixin(MapItem.class)
public abstract class MapItemMixin {
	@WrapOperation(method = "inventoryTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/MapItem;update(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V"))
	private void mapplus$fillCaveMap(MapItem item, Level level, Entity holder, MapItemSavedData map, Operation<Void> original,
			@Local(argsOnly = true) ItemStack stack) {
		if (!(level instanceof ServerLevel serverLevel) || !CaveMaps.update(serverLevel, holder, map, stack.get(DataComponents.MAP_ID))) {
			original.call(item, level, holder, map);
		}
	}

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
