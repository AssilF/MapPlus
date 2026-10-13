package com.assilf.mapplus.mixin;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.ink.DrawingDesk;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

	/** Whether the holder being looked at only views the map and doesn't carry it. */
	@Unique
	private boolean mapplus$onlyViewing;

	@WrapOperation(method = "tickCarriedBy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;contains(Ljava/util/function/Predicate;)Z"))
	private boolean mapplus$keepViewers(Inventory inventory, Predicate<ItemStack> matcher, Operation<Boolean> original,
			@Local(argsOnly = true) ItemStack carried) {
		boolean carries = original.call(inventory, matcher);
		mapplus$onlyViewing = !carries && DrawingDesk.isViewing(inventory.player, carried.get(DataComponents.MAP_ID));
		return carries || mapplus$onlyViewing;
	}

	/** A player who only views a map keeps receiving it, but doesn't show on it. */
	@WrapWithCondition(method = "tickCarriedBy", at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;addDecoration(Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/LevelAccessor;Ljava/lang/String;DDDLnet/minecraft/network/chat/Component;)V"))
	private boolean mapplus$hideViewers(MapItemSavedData map, Holder<MapDecorationType> type, LevelAccessor level, String id,
			double x, double z, double rotation, Component name) {
		if (mapplus$onlyViewing) {
			((MapItemSavedDataAccessor) map).mapplus$removeDecoration(id);
			return false;
		}
		return true;
	}
}
