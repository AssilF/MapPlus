package com.assilf.mapplus.mixin;

import com.assilf.mapplus.ink.InkData;
import com.assilf.mapplus.survey.CaveMaps;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EmptyMapItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Remembers who made each map, so the maker can always scrape a seal off it,
 * whoever sealed it, and makes maps used deep underground cave maps of that
 * level. Kept in MapPlus's own data.
 */
@Mixin(EmptyMapItem.class)
public abstract class EmptyMapItemMixin {
	@ModifyExpressionValue(method = "use", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/MapItem;create(Lnet/minecraft/server/level/ServerLevel;IIBZZ)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack mapplus$recordMaker(ItemStack map, Level level, Player player, InteractionHand hand) {
		MapId mapId = map.get(DataComponents.MAP_ID);
		if (mapId != null && level instanceof ServerLevel serverLevel) {
			InkData.get(serverLevel.getServer()).setMaker(mapId, player.getUUID());
			if (CaveMaps.isUnderground(serverLevel, player)) {
				CaveMaps.makeCaveMap(serverLevel, player, map);
			}
		}
		return map;
	}
}
