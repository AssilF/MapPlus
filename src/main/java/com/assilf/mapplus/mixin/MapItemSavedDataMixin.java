package com.assilf.mapplus.mixin;

import com.assilf.mapplus.atlas.Atlas;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/**
 * A player carrying an atlas counts as carrying every map bound into it, so
 * those maps keep showing the player and sending updates.
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
}
