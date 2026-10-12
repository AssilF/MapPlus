package com.assilf.mapplus;

import com.assilf.mapplus.atlas.AtlasManager;
import com.assilf.mapplus.ink.CauldronWashing;
import com.assilf.mapplus.ink.InkManager;
import com.assilf.mapplus.ink.InkSupply;
import com.assilf.mapplus.net.AtlasActionPayload;
import com.assilf.mapplus.net.InkSealPayload;
import com.assilf.mapplus.net.InkStatePayload;
import com.assilf.mapplus.net.InkStrokePayload;
import com.assilf.mapplus.net.OpenInkPayload;
import com.assilf.mapplus.survey.SpyglassSurvey;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapPlus implements ModInitializer {
	public static final String MOD_ID = "mapplus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		MapPlusConfig.load();
		InkSupply.init();

		PayloadTypeRegistry.serverboundPlay().register(OpenInkPayload.TYPE, OpenInkPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(InkStrokePayload.TYPE, InkStrokePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(InkSealPayload.TYPE, InkSealPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AtlasActionPayload.TYPE, AtlasActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(InkStatePayload.TYPE, InkStatePayload.CODEC);

		InkManager.register();
		CauldronWashing.register();
		SpyglassSurvey.register();
		AtlasManager.register();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
