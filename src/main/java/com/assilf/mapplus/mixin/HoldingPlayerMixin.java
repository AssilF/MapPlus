package com.assilf.mapplus.mixin;

import com.assilf.mapplus.ink.InkData;
import com.assilf.mapplus.ink.InkLayer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/**
 * Draws ink into the map pixels sent to players. Only the packet changes; the
 * map's saved colors never do, so vanilla clients see ink and removing the mod
 * leaves maps untouched.
 */
@Mixin(MapItemSavedData.HoldingPlayer.class)
public abstract class HoldingPlayerMixin {
	@Shadow
	@Final
	public Player player;

	@ModifyReturnValue(method = "nextUpdatePacket", at = @At("RETURN"))
	private Packet<?> mapplus$drawInk(Packet<?> packet, MapId mapId) {
		if (!(packet instanceof ClientboundMapItemDataPacket mapPacket) || mapPacket.colorPatch().isEmpty()) {
			return packet;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return packet;
		}
		InkLayer layer = InkData.get(server).find(mapId);
		if (layer == null) {
			return packet;
		}

		MapItemSavedData.MapPatch patch = mapPacket.colorPatch().get();
		byte[] colors = patch.mapColors().clone();
		layer.overlay(patch.startX(), patch.startY(), patch.width(), patch.height(), colors);
		return new ClientboundMapItemDataPacket(
				mapPacket.mapId(),
				mapPacket.scale(),
				mapPacket.locked(),
				mapPacket.decorations(),
				Optional.of(new MapItemSavedData.MapPatch(patch.startX(), patch.startY(), patch.width(), patch.height(), colors))
		);
	}
}
