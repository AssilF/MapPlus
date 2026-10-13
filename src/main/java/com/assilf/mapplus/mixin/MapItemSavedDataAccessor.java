package com.assilf.mapplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Map;

@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataAccessor {
	@Invoker("setColorsDirty")
	void mapplus$setColorsDirty(int x, int z);

	@Invoker("setDecorationsDirty")
	void mapplus$setDecorationsDirty();

	@Invoker("removeDecoration")
	void mapplus$removeDecoration(String id);

	@Accessor("carriedBy")
	List<MapItemSavedData.HoldingPlayer> mapplus$carriedBy();

	@Accessor("carriedByPlayers")
	Map<Player, MapItemSavedData.HoldingPlayer> mapplus$carriedByPlayers();
}
