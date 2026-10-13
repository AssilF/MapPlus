package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.client.ink.ClientTints;
import com.assilf.mapplus.ink.Ink;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blends a map's watered down ink over its colors when the map's picture is made. */
@Mixin(targets = "net.minecraft.client.resources.MapTextureManager$MapInstance")
public abstract class MapInstanceMixin {
	@Shadow
	@Final
	private DynamicTexture texture;

	@Unique
	private int mapplus$mapId;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mapplus$rememberId(MapTextureManager manager, int mapId, MapItemSavedData data, CallbackInfo ci) {
		mapplus$mapId = mapId;
	}

	@Inject(method = "updateTextureIfNeeded", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
	private void mapplus$blendTints(CallbackInfo ci) {
		byte[] tints = ClientTints.get(mapplus$mapId);
		NativeImage pixels = texture.getPixels();
		if (tints == null || pixels == null) {
			return;
		}
		for (int i = 0; i < Ink.PIXELS; i++) {
			if (tints[i] != Ink.NONE) {
				int x = i % Ink.SIZE;
				int y = i / Ink.SIZE;
				pixels.setPixel(x, y, ClientTints.blend(pixels.getPixel(x, y), tints[i]));
			}
		}
	}
}
