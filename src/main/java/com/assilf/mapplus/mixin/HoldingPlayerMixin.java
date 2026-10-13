package com.assilf.mapplus.mixin;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.ink.InkData;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.InkLayer;
import com.assilf.mapplus.ink.MarkDecorations;
import com.assilf.mapplus.net.MapMarksPayload;
import com.assilf.mapplus.nation.Nations;
import com.assilf.mapplus.nation.Territory;
import com.assilf.mapplus.net.MapInkPayload;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * Draws ink and nations' territory into the map pixels sent to players, and
 * adds the map's marks to the markers sent to players without the mod. Only the packet changes; the
 * map's saved data never does, so vanilla clients see ink and marks, and
 * removing the mod leaves maps untouched. Players with the mod are sent the
 * marks and the watered down ink themselves instead, whenever they change, and
 * draw them over the map.
 */
@Mixin(MapItemSavedData.HoldingPlayer.class)
public abstract class HoldingPlayerMixin {
	@Shadow
	@Final
	public Player player;

	/** The version of the map's marks this player was last sent, or 0 for none. */
	@Unique
	private int mapplus$marksSent;

	/** The version of the map's ink and territory this player was last sent, and whether there was any. */
	@Unique
	private int mapplus$inkSent;
	@Unique
	private boolean mapplus$hadInk;

	/** The territory this player was last sent the whole map for. */
	@Unique
	private int mapplus$territorySent;

	@Shadow
	private void markColorsDirty(int x, int y) {
	}

	/** When a map's territory changes, the whole map is sent again, since borders can move anywhere on it. */
	@Inject(method = "nextUpdatePacket", at = @At("HEAD"))
	private void mapplus$resendTerritory(MapId mapId, CallbackInfoReturnable<Packet<?>> cir) {
		MinecraftServer server = player.level().getServer();
		MapItemSavedData map = player.level().getMapData(mapId);
		if (server == null || map == null) {
			return;
		}
		int version = Nations.territory(server, mapId, map).version();
		if (version != mapplus$territorySent) {
			mapplus$territorySent = version;
			markColorsDirty(0, 0);
			markColorsDirty(Ink.SIZE - 1, Ink.SIZE - 1);
		}
	}

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
		MapItemSavedData map = player.level().getMapData(mapId);
		Territory territory = map == null ? Territory.NONE : Nations.territory(server, mapId, map);
		if (layer == null && territory.isEmpty()) {
			return packet;
		}

		boolean modded = player instanceof ServerPlayer serverPlayer && MapPlus.hasMod(serverPlayer);
		// Sent before the pixels, so the map is blended with the tints that go with them.
		int inkVersion = 31 * (layer == null ? 0 : layer.inkVersion()) + territory.version();
		if (modded && mapplus$inkSent != inkVersion) {
			mapplus$inkSent = inkVersion;
			byte[] ink = layer == null ? new byte[Ink.PIXELS] : layer.pixels().clone();
			territory.addTo(ink);
			boolean hasInk = !territory.isEmpty() || layer != null && !layer.isEmpty();
			if (hasInk || mapplus$hadInk) {
				ServerPlayNetworking.send((ServerPlayer) player, new MapInkPayload(mapId.id(), ink));
			}
			mapplus$hadInk = hasInk;
		}

		Optional<MapItemSavedData.MapPatch> patch = mapPacket.colorPatch().map(colors -> {
			byte[] inked = colors.mapColors().clone();
			territory.overlay(colors.startX(), colors.startY(), colors.width(), colors.height(), inked, !modded);
			if (layer != null) {
				layer.overlay(colors.startX(), colors.startY(), colors.width(), colors.height(), inked, !modded);
			}
			return new MapItemSavedData.MapPatch(colors.startX(), colors.startY(), colors.width(), colors.height(), inked);
		});

		if (layer == null) {
			return new ClientboundMapItemDataPacket(mapPacket.mapId(), mapPacket.scale(), mapPacket.locked(), mapPacket.decorations(), patch);
		}
		Optional<List<MapDecoration>> decorations = mapPacket.decorations();
		if (!modded && decorations.isPresent() && !layer.marks().isEmpty()) {
			decorations = Optional.of(MarkDecorations.withMarks(decorations.get(), layer.marks()));
		}
		// Sent with the markers, which are resent whenever the marks change.
		if (modded && mapPacket.decorations().isPresent() && mapplus$marksSent != layer.marksVersion()) {
			mapplus$marksSent = layer.marksVersion();
			ServerPlayNetworking.send((ServerPlayer) player, new MapMarksPayload(mapId.id(), List.copyOf(layer.marks())));
		}

		return new ClientboundMapItemDataPacket(mapPacket.mapId(), mapPacket.scale(), mapPacket.locked(), decorations, patch);
	}
}
