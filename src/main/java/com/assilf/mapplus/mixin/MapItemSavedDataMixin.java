package com.assilf.mapplus.mixin;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.ink.DrawingDesk;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/**
 * A player carrying an atlas counts as carrying every map stitched into it, so
 * those maps keep showing the player and sending updates. A player looking at
 * a map on a drawing desk or a lectern keeps receiving its updates too.
 */
@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin {
	@ModifyReturnValue(method = "mapMatcher", at = @At("RETURN"))
	private static Predicate<ItemStack> mapplus$matchAtlases(Predicate<ItemStack> original, ItemStack map) {
		MapId mapId = map.get(DataComponents.MAP_ID);
		if (mapId == null) {
			return original;
		}
		return original.or(stack -> Atlas.isBook(stack) && Atlas.read(stack).map(atlas -> atlas.contains(mapId)).orElse(false));
	}

	@WrapOperation(method = "tickCarriedBy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;contains(Ljava/util/function/Predicate;)Z"))
	private boolean mapplus$keepViewers(Inventory inventory, Predicate<ItemStack> matcher, Operation<Boolean> original,
			@Local(argsOnly = true) ItemStack carried) {
		return original.call(inventory, matcher) || DrawingDesk.isViewing(inventory.player, carried.get(DataComponents.MAP_ID));
	}
}
