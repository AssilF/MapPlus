package com.assilf.mapplus.client.foresight;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.client.ink.ClientInk;
import com.assilf.mapplus.client.marks.ClientMarks;
import com.assilf.mapplus.client.marks.Labels;
import com.assilf.mapplus.client.marks.MarkRenderer;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.Mark;
import com.assilf.mapplus.net.MapPlacePayload;
import com.assilf.mapplus.survey.MapPixels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.levelgen.Heightmap;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.sdl.SDLScancode;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Foresight: wearing a helmet with it, the map in hand is projected onto the
 * land around you, as a ghostly blueprint: its ink laid on the ground, its
 * labels written across it, and a pillar of light at each pin and icon.
 * Nothing is placed in the world; only the wearer sees it, and only with the
 * mod. Where the map lies comes from the server (see {@link MapPlaces}): the
 * game's own copy of a map doesn't know.
 */
public final class Projection {
	public static final ResourceKey<Enchantment> FORESIGHT = ResourceKey.create(Registries.ENCHANTMENT, MapPlus.id("foresight"));
	/** How far around the wearer the map is projected, in blocks: the whole of a map at its most detailed. */
	private static final int REACH = 96;
	/** How see-through full ink is on the ground; watered down ink is fainter, but never too faint to see. */
	private static final float GROUND_OPACITY = 0.6F;
	private static final float FAINTEST_ON_GROUND = 0.45F;
	/** How high over the ground the projection floats, enough not to flicker against it. */
	private static final float LIFT = 0.07F;
	private static final float PILLAR_HEIGHT = 3.0F;
	private static final float PILLAR_HALF_WIDTH = 0.2F;
	private static final int FULL_BRIGHT = 0xF000F0;
	/** How often the projection is worked out again, in ticks; between times the same one is drawn. */
	private static final int REBUILD_TICKS = 2;
	/** The projection fades out over this many blocks at the edge of its reach. */
	private static final float FADE = 16.0F;
	/** A shimmer runs out through the projection from the wearer: how deep it goes, how fast and how far apart its waves are. */
	private static final float SHIMMER = 0.3F;
	private static final float SHIMMER_SPEED = 0.18F;
	private static final float SHIMMER_SPACING = 0.3F;

	/** How the projection shows: all of the ink, only the edges of what's drawn, or not at all. */
	public enum Mode {
		FULL, EDGES, OFF;

		Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		Component title() {
			return Component.translatableWithFallback("mapplus.foresight." + name().toLowerCase(java.util.Locale.ROOT), switch (this) {
				case FULL -> "Foresight: all of the map";
				case EDGES -> "Foresight: edges only";
				case OFF -> "Foresight: off";
			});
		}
	}

	/** The key that changes how the projection shows. */
	private static final KeyMapping TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mapplus.foresight",
			InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_O, KeyMapping.Category.MISC));
	private static Mode mode = Mode.FULL;

	/** The projection worked out last: a square of ground per inked block, and the marks standing on it. */
	private record Ground(float[] corners, int[] colors, float[] distances, int count, List<Mark> marks, float[] markPositions,
			MapItemSavedData map) {
	}

	private static Ground ground;
	private static int ticks;

	private Projection() {
	}

	public static void register() {
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(Projection::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE.consumeClick()) {
				mode = mode.next();
				if (client.player != null) {
					client.player.sendOverlayMessage(mode.title());
				}
				ground = null;
			}
			if (++ticks % REBUILD_TICKS == 0) {
				ground = mode == Mode.OFF ? null : build(client);
			}
		});
	}

	private static boolean wearsForesight(LocalPlayer player) {
		ItemEnchantments enchantments = player.getItemBySlot(EquipmentSlot.HEAD).getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			if (enchantment.is(FORESIGHT)) {
				return true;
			}
		}
		return false;
	}

	/** The map in hand: a map, or the open spread's map of an atlas. The main hand first. */
	private static MapId heldMap(LocalPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(Items.FILLED_MAP) && stack.get(DataComponents.MAP_ID) != null) {
				return stack.get(DataComponents.MAP_ID);
			}
			MapId atlasMap = Atlas.read(stack).flatMap(Atlas::selectedPlate).map(Atlas.Plate::id).orElse(null);
			if (atlasMap != null) {
				return atlasMap;
			}
		}
		return null;
	}

	/** The top of the ground at a column, or the lowest the world goes where it isn't loaded. */
	private static int groundAt(ClientLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
	}

	/** Works the projection out: every block around the wearer the map has ink over, and the marks in reach. */
	private static Ground build(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || !wearsForesight(player)) {
			return null;
		}
		MapId mapId = heldMap(player);
		MapItemSavedData map = mapId == null ? null : level.getMapData(mapId);
		MapPlacePayload place = mapId == null ? null : MapPlaces.get(mapId.id());
		if (map == null || place == null || !map.dimension.equals(level.dimension())) {
			return null;
		}
		byte[] ink = ClientInk.get(mapId.id());
		List<Mark> allMarks = ClientMarks.get(mapId);
		int px0 = player.getBlockX();
		int pz0 = player.getBlockZ();

		int capacity = (2 * REACH + 1) * (2 * REACH + 1);
		float[] corners = new float[ink == null ? 0 : capacity * 3];
		int[] colors = new int[ink == null ? 0 : capacity];
		float[] distances = new float[ink == null ? 0 : capacity];
		int count = 0;
		if (ink != null) {
			for (int z = pz0 - REACH; z <= pz0 + REACH; z++) {
				for (int x = px0 - REACH; x <= px0 + REACH; x++) {
					int distance2 = (x - px0) * (x - px0) + (z - pz0) * (z - pz0);
					if (distance2 > REACH * REACH) {
						continue;
					}
					int px = MapPlaces.pixelX(place, map, x);
					int pz = MapPlaces.pixelZ(place, map, z);
					if (px < 0 || pz < 0 || px >= Ink.SIZE || pz >= Ink.SIZE) {
						continue;
					}
					byte pixel = ink[px + pz * Ink.SIZE];
					if (!Ink.isPresent(pixel) || mode == Mode.EDGES && !isEdge(ink, px, pz, pixel)) {
						continue;
					}
					int top = groundAt(level, x, z);
					if (top <= level.getMinY()) {
						continue;
					}
					float edge = Math.min(1.0F, (REACH - (float) Math.sqrt(distance2)) / FADE);
					int alpha = Math.round(GROUND_OPACITY * Math.max(FAINTEST_ON_GROUND, Ink.opacity(Ink.strength(pixel))) * edge * 255);
					if (alpha <= 0) {
						continue;
					}
					corners[count * 3] = x;
					corners[count * 3 + 1] = top + LIFT;
					corners[count * 3 + 2] = z;
					distances[count] = Ink.isGlow(pixel) ? -1 : (float) Math.sqrt(distance2);
					colors[count] = alpha << 24 | Ink.dye(pixel).getMapColor().calculateARGBColor(Ink.brightness(pixel)) & 0xFFFFFF;
					count++;
				}
			}
		}

		List<Mark> marks = new ArrayList<>();
		float[] positions = new float[allMarks.size() * 3];
		int scale = MapPixels.scaleBlocks(map);
		for (Mark mark : allMarks) {
			float x = MapPlaces.worldX(place, map, mark.x()) + scale / 2.0F;
			float z = MapPlaces.worldZ(place, map, mark.y()) + scale / 2.0F;
			if (Math.abs(x - player.getX()) > REACH || Math.abs(z - player.getZ()) > REACH) {
				continue;
			}
			int top = groundAt(level, (int) Math.floor(x), (int) Math.floor(z));
			if (top <= level.getMinY()) {
				continue;
			}
			positions[marks.size() * 3] = x;
			positions[marks.size() * 3 + 1] = top;
			positions[marks.size() * 3 + 2] = z;
			marks.add(mark);
		}
		return count == 0 && marks.isEmpty() ? null : new Ground(corners, colors, distances, count, marks, positions, map);
	}

	/** Whether a pixel of ink is on the edge of what's drawn: beside a pixel of other ink, or none, or the map's edge. */
	private static boolean isEdge(byte[] ink, int px, int pz, byte pixel) {
		return px == 0 || pz == 0 || px == Ink.SIZE - 1 || pz == Ink.SIZE - 1 || ink[px - 1 + pz * Ink.SIZE] != pixel
				|| ink[px + 1 + pz * Ink.SIZE] != pixel || ink[px + (pz - 1) * Ink.SIZE] != pixel || ink[px + (pz + 1) * Ink.SIZE] != pixel;
	}

	/** How many squares of ground the projection covers now, for tests. */
	public static int projectedSquares() {
		Ground projected = ground;
		return projected == null ? 0 : projected.count();
	}

	private static void render(LevelRenderContext context) {
		Ground projected = ground;
		if (projected == null) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack pose = context.poseStack();
		pose.pushPose();
		pose.translate(-camera.x, -camera.y, -camera.z);

		if (projected.count() > 0) {
			float time = System.currentTimeMillis() % 3_600_000L / 50.0F;
			context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (at, vertices) -> {
				float[] corners = projected.corners();
				for (int i = 0; i < projected.count(); i++) {
					float x = corners[i * 3];
					float y = corners[i * 3 + 1];
					float z = corners[i * 3 + 2];
					int color = projected.colors()[i];
					float distance = projected.distances()[i];
					if (distance >= 0) {
						// Glow ink stays bright; the rest shimmers with the waves going out.
						float wave = 1.0F - SHIMMER * (0.5F + 0.5F * (float) Math.sin(time * SHIMMER_SPEED - distance * SHIMMER_SPACING));
						color = Math.round((color >>> 24) * wave) << 24 | color & 0xFFFFFF;
					}
					vertices.addVertex(at, x, y, z).setColor(color);
					vertices.addVertex(at, x, y, z + 1).setColor(color);
					vertices.addVertex(at, x + 1, y, z + 1).setColor(color);
					vertices.addVertex(at, x + 1, y, z).setColor(color);
				}
			});
		}

		Font font = Minecraft.getInstance().font;
		int scale = MapPixels.scaleBlocks(projected.map());
		for (int i = 0; i < projected.marks().size(); i++) {
			Mark mark = projected.marks().get(i);
			float x = projected.markPositions()[i * 3];
			float top = projected.markPositions()[i * 3 + 1];
			float z = projected.markPositions()[i * 3 + 2];
			if (mark.isLabel()) {
				// Written across the ground, north up, as big as it is on the map.
				FormattedCharSequence text = Component.literal(mark.text()).getVisualOrderText();
				float size = Labels.fit(mark.text(), mark.size(), mark.x(), mark.y()).scale() * scale;
				pose.pushPose();
				pose.translate(x, top + LIFT * 2, z);
				pose.mulPose(new Matrix4f().rotationX((float) Math.toRadians(90.0)));
				pose.scale(size, size, size);
				context.submitNodeCollector().submitText(pose, -font.width(text) / 2.0F, -4.0F, text, false, Font.DisplayMode.NORMAL, FULL_BRIGHT,
						MarkRenderer.inkColor(mark), 0, 0);
				pose.popPose();
			} else {
				int color = (mark.kind() == Mark.Kind.PIN ? Labels.color(mark.color()) : MarkRenderer.inkColor(mark)) & 0xFFFFFF | 0xA0000000;
				pillar(context, pose, x, top, z, color);
			}
		}
		pose.popPose();
	}

	/** A thin see-through pillar of light standing at a pin or icon. */
	private static void pillar(LevelRenderContext context, PoseStack pose, float x, float ground, float z, int color) {
		float x0 = x - PILLAR_HALF_WIDTH;
		float x1 = x + PILLAR_HALF_WIDTH;
		float z0 = z - PILLAR_HALF_WIDTH;
		float z1 = z + PILLAR_HALF_WIDTH;
		float y0 = ground;
		float y1 = ground + PILLAR_HEIGHT;
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (at, vertices) -> {
			// Each side, facing out.
			vertices.addVertex(at, x0, y0, z0).setColor(color);
			vertices.addVertex(at, x1, y0, z0).setColor(color);
			vertices.addVertex(at, x1, y1, z0).setColor(color);
			vertices.addVertex(at, x0, y1, z0).setColor(color);

			vertices.addVertex(at, x1, y0, z1).setColor(color);
			vertices.addVertex(at, x0, y0, z1).setColor(color);
			vertices.addVertex(at, x0, y1, z1).setColor(color);
			vertices.addVertex(at, x1, y1, z1).setColor(color);

			vertices.addVertex(at, x0, y0, z1).setColor(color);
			vertices.addVertex(at, x0, y0, z0).setColor(color);
			vertices.addVertex(at, x0, y1, z0).setColor(color);
			vertices.addVertex(at, x0, y1, z1).setColor(color);

			vertices.addVertex(at, x1, y0, z0).setColor(color);
			vertices.addVertex(at, x1, y0, z1).setColor(color);
			vertices.addVertex(at, x1, y1, z1).setColor(color);
			vertices.addVertex(at, x1, y1, z0).setColor(color);
		});
	}
}
