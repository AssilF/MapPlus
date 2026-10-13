package com.assilf.mapplus.mixin;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.ink.InkData;
import com.assilf.mapplus.ink.InkLayer;
import com.assilf.mapplus.ink.MarkDecorations;
import com.assilf.mapplus.net.MapMarksPayload;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Optional;

/**
 * Draws ink into the map pixels sent to players, and adds the map's labels and
 * pins to the markers sent with them. Only the packet changes; the map's saved
 * data never does, so vanilla clients see ink and pins, and removing the mod
 * leaves maps untouched. Players with the mod are sent the labels and pins
 * themselves too, whenever they change.
 */
@Mixin(MapItemSavedData.HoldingPlayer.class)
public abstract class HoldingPlayerMixin {
	@Shadow
	@Final
	public Player player;

	/** The version of the map's marks this player was last sent, or 0 for none. */
	@Unique
	private int mapplus$marksSent;

	@ModifyReturnValue(method = "nextUpdatePacket", at = @At("RETURN"))
	private Packet<?> mapplus$drawInk(Packet<?> packet, MapId mapId) {
		if (!(packet instanceof ClientboundMapItemDataPacket mapPacket)) {
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

		Optional<MapItemSavedData.MapPatch> patch = mapPacket.colorPatch().map(colors -> {
			byte[] inked = colors.mapColors().clone();
			layer.overlay(colors.startX(), colors.startY(), colors.width(), colors.height(), inked);
			return new MapItemSavedData.MapPatch(colors.startX(), colors.startY(), colors.width(), colors.height(), inked);
		});

		boolean modded = player instanceof ServerPlayer serverPlayer && MapPlus.hasMod(serverPlayer);
		Optional<List<MapDecoration>> decorations = mapPacket.decorations();
		if (decorations.isPresent() && !layer.marks().isEmpty()) {
			decorations = Optional.of(MarkDecorations.withMarks(decorations.get(), layer.marks(), !modded));
		}
		// Sent with the markers, which are resent whenever the marks change.
		if (modded && mapPacket.decorations().isPresent() && mapplus$marksSent != layer.marksVersion()) {
			mapplus$marksSent = layer.marksVersion();
			ServerPlayNetworking.send((ServerPlayer) player, new MapMarksPayload(mapId.id(), List.copyOf(layer.marks())));
		}

		return new ClientboundMapItemDataPacket(mapPacket.mapId(), mapPacket.scale(), mapPacket.locked(), decorations, patch);
	}
}
