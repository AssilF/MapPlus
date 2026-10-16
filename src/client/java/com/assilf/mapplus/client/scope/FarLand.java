package com.assilf.mapplus.client.scope;

import com.assilf.mapplus.net.FarViewPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The land past the render distance, seen through a spyglass: what the server
 * sends of it (see {@code FarView}), drawn as blocks of map color at their
 * heights, shaded by slope as a map is, and hazed with distance. It fades in
 * as the spyglass goes up and out as it comes down.
 */
public final class FarLand {
	/** The most chunks of far land kept; the least recently sent go first. */
	private static final int MOST_KEPT = 8192;
	/** How quickly the far land fades in and out, a share a tick. */
	private static final float FADE = 0.2F;
	/** How wide the view is looked over for far land to draw, either way of the line of sight. */
	private static final double DRAWN_COS = Math.cos(Math.toRadians(40.0));
	/** The haze far land fades into. */
	private static final int HAZE = 0xB9C9DB;

	private static final Map<Long, FarViewPayload.Patch> PATCHES = new LinkedHashMap<>(256, 0.75F, false) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, FarViewPayload.Patch> eldest) {
			return size() > MOST_KEPT;
		}
	};
	private static ClientLevel level;
	private static float shown;
	private static float shownBefore;

	private FarLand() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FarViewPayload.TYPE, (payload, context) -> {
			for (FarViewPayload.Patch patch : payload.patches()) {
				PATCHES.put(key(patch.chunkX(), patch.chunkZ()), patch);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != level) {
				level = client.level;
				PATCHES.clear();
			}
			shownBefore = shown;
			boolean scoping = client.player != null && client.player.isScoping();
			shown = Math.clamp(shown + (scoping ? FADE : -FADE), 0.0F, 1.0F);
		});
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(FarLand::render);
	}

	/** How many chunks of far land are kept, for tests. */
	public static int kept() {
		return PATCHES.size();
	}

	private static long key(int chunkX, int chunkZ) {
		return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (Math.max(shown, shownBefore) <= 0.0F || PATCHES.isEmpty() || minecraft.player == null) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Vec3 look = minecraft.player.getViewVector(1.0F);
		double lookLength = Math.sqrt(look.x * look.x + look.z * look.z);
		int renderDistance = minecraft.options.getEffectiveRenderDistance();
		int cameraChunkX = (int) Math.floor(camera.x) >> 4;
		int cameraChunkZ = (int) Math.floor(camera.z) >> 4;
		int alpha = Math.round(255 * Math.max(shown, shownBefore));

		PoseStack pose = context.poseStack();
		pose.pushPose();
		pose.translate(-camera.x, -camera.y, -camera.z);
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (at, vertices) -> {
			for (FarViewPayload.Patch patch : PATCHES.values()) {
				int dx = patch.chunkX() - cameraChunkX;
				int dz = patch.chunkZ() - cameraChunkZ;
				// Within the render distance the real land shows.
				if (dx * dx + dz * dz < renderDistance * renderDistance) {
					continue;
				}
				double toX = (patch.chunkX() << 4) + 8 - camera.x;
				double toZ = (patch.chunkZ() << 4) + 8 - camera.z;
				double distance = Math.sqrt(toX * toX + toZ * toZ);
				if (lookLength > 1.0E-3 && (toX * look.x + toZ * look.z) / (distance * lookLength) < DRAWN_COS) {
					continue;
				}
				float haze = (float) Math.clamp((distance - renderDistance * 16.0) / (renderDistance * 16.0 * 4.0), 0.0, 0.6);
				draw(at, vertices, patch, haze, alpha);
			}
		});
		pose.popPose();
	}

	private static void draw(PoseStack.Pose at, VertexConsumer vertices, FarViewPayload.Patch patch, float haze, int alpha) {
		int step = FarViewPayload.STEP;
		int side = FarViewPayload.SIDE;
		FarViewPayload.Patch east = PATCHES.get(key(patch.chunkX() + 1, patch.chunkZ()));
		FarViewPayload.Patch south = PATCHES.get(key(patch.chunkX(), patch.chunkZ() + 1));
		FarViewPayload.Patch north = PATCHES.get(key(patch.chunkX(), patch.chunkZ() - 1));
		for (int i = 0; i < FarViewPayload.CELLS; i++) {
			int colorId = patch.colors()[i] & 0xFF;
			if (colorId == 0) {
				continue;
			}
			int cx = i % side;
			int cz = i / side;
			float x = (patch.chunkX() << 4) + cx * step;
			float z = (patch.chunkZ() << 4) + cz * step;
			int height = patch.heights()[i];
			float top = height + 1.0F;
			// Shaded by slope as a map is: lighter where the land rises to the south, darker where it falls.
			int northHeight = cz > 0 ? patch.heights()[i - side] : north != null ? north.heights()[i + side * (side - 1)] : height;
			MapColor.Brightness brightness = height > northHeight ? MapColor.Brightness.HIGH
					: height < northHeight ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
			int color = tint(MapColor.byId(colorId).calculateARGBColor(brightness), haze, alpha);
			vertices.addVertex(at, x, top, z).setColor(color);
			vertices.addVertex(at, x, top, z + step).setColor(color);
			vertices.addVertex(at, x + step, top, z + step).setColor(color);
			vertices.addVertex(at, x + step, top, z).setColor(color);

			// Cliffs between cells, so the land doesn't float in tiles.
			int sideColor = tint(MapColor.byId(colorId).calculateARGBColor(MapColor.Brightness.LOWEST), haze, alpha);
			int eastHeight = cx < side - 1 ? patch.heights()[i + 1] : east != null ? east.heights()[i - (side - 1)] : height;
			if (eastHeight != height) {
				float low = Math.min(eastHeight, height) + 1.0F;
				float high = Math.max(eastHeight, height) + 1.0F;
				vertices.addVertex(at, x + step, low, z).setColor(sideColor);
				vertices.addVertex(at, x + step, high, z).setColor(sideColor);
				vertices.addVertex(at, x + step, high, z + step).setColor(sideColor);
				vertices.addVertex(at, x + step, low, z + step).setColor(sideColor);
			}
			int southHeight = cz < side - 1 ? patch.heights()[i + side] : south != null ? south.heights()[cx] : height;
			if (southHeight != height) {
				float low = Math.min(southHeight, height) + 1.0F;
				float high = Math.max(southHeight, height) + 1.0F;
				vertices.addVertex(at, x, low, z + step).setColor(sideColor);
				vertices.addVertex(at, x, high, z + step).setColor(sideColor);
				vertices.addVertex(at, x + step, high, z + step).setColor(sideColor);
				vertices.addVertex(at, x + step, low, z + step).setColor(sideColor);
			}
		}
	}

	/** A map color, hazed toward the sky with distance, at the far land's fade. */
	private static int tint(int argb, float haze, int alpha) {
		int r = Math.round(((argb >> 16) & 0xFF) * (1 - haze) + ((HAZE >> 16) & 0xFF) * haze);
		int g = Math.round(((argb >> 8) & 0xFF) * (1 - haze) + ((HAZE >> 8) & 0xFF) * haze);
		int b = Math.round((argb & 0xFF) * (1 - haze) + (HAZE & 0xFF) * haze);
		return alpha << 24 | r << 16 | g << 8 | b;
	}
}
