package com.assilf.mapplus;

import com.assilf.mapplus.atlas.AtlasManager;
import com.assilf.mapplus.atlas.LecternBooks;
import com.assilf.mapplus.ink.CauldronWashing;
import com.assilf.mapplus.ink.FramePainting;
import com.assilf.mapplus.ink.Sketching;
import com.assilf.mapplus.ink.InkManager;
import com.assilf.mapplus.ink.InkSupply;
import com.assilf.mapplus.ink.MapViewers;
import com.assilf.mapplus.ink.MarkManager;
import com.assilf.mapplus.nation.Marches;
import com.assilf.mapplus.nation.Nations;
import com.assilf.mapplus.net.AtlasPagePayload;
import com.assilf.mapplus.net.FramePaintPayload;
import com.assilf.mapplus.net.InkLayerPayload;
import com.assilf.mapplus.net.InkStatePayload;
import com.assilf.mapplus.net.InkStrokePayload;
import com.assilf.mapplus.net.MapMarksPayload;
import com.assilf.mapplus.net.MapInkPayload;
import com.assilf.mapplus.net.FarViewPayload;
import com.assilf.mapplus.net.LecternBookPayload;
import com.assilf.mapplus.net.MapPlacePayload;
import com.assilf.mapplus.net.MarkEditPayload;
import com.assilf.mapplus.net.SketchPayload;
import com.assilf.mapplus.survey.CaveMaps;
import com.assilf.mapplus.survey.FarView;
import com.assilf.mapplus.survey.Revealing;
import com.assilf.mapplus.survey.SpyglassSurvey;
import com.assilf.mapplus.survey.Wayfinding;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;

public class MapPlus implements ModInitializer {
	public static final String MOD_ID = "mapplus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Whether the server the client plays on has the mod. Set by the client initializer. */
	public static BooleanSupplier serverHasMod = () -> false;

	@Override
	public void onInitialize() {
		MapPlusConfig.load();
		InkSupply.init();

		PayloadTypeRegistry.serverboundPlay().register(InkStrokePayload.TYPE, InkStrokePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AtlasPagePayload.TYPE, AtlasPagePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MarkEditPayload.TYPE, MarkEditPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SketchPayload.TYPE, SketchPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(FramePaintPayload.TYPE, FramePaintPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(InkStatePayload.TYPE, InkStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MapMarksPayload.TYPE, MapMarksPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(InkLayerPayload.TYPE, InkLayerPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MapInkPayload.TYPE, MapInkPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MapPlacePayload.TYPE, MapPlacePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FarViewPayload.TYPE, FarViewPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LecternBookPayload.TYPE, LecternBookPayload.CODEC);

		InkManager.register();
		MarkManager.register();
		MapViewers.register();
		CauldronWashing.register();
		SpyglassSurvey.register();
		FarView.register();
		Wayfinding.register();
		Revealing.register();
		Nations.register();
		Marches.register();
		CaveMaps.register();
		FramePainting.register();
		Sketching.register();
		AtlasManager.register();
		LecternBooks.register();
	}

	/**
	 * Whether both sides of this player's connection have the mod. Only then does
	 * the cartography table draw; everyone else gets the vanilla table.
	 */
	public static boolean hasMod(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			return serverPlayer.connection != null && ServerPlayNetworking.canSend(serverPlayer, InkStatePayload.TYPE);
		}
		return player.level().isClientSide() && serverHasMod.getAsBoolean();
	}

	/** Whether a player may use operator commands. */
	public static boolean isOperator(Player player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
