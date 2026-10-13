package com.assilf.mapplus.client.foresight;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.client.ink.ClientInk;
import com.assilf.mapplus.client.marks.ClientMarks;
import com.assilf.mapplus.client.marks.Labels;
import com.assilf.mapplus.client.marks.MarkRenderer;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.Mark;
import com.assilf.mapplus.survey.MapPixels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Foresight: wearing a helmet with it, the map in hand is projected onto the
 * land around you, as a ghostly blueprint: its ink laid on the ground, its
 * labels written across it, and a pillar of light at each pin and icon.
 * Nothing is placed in the world; only the wearer sees it.
 */
public final class Projection {
	public static final ResourceKey<Enchantment> FORESIGHT = ResourceKey.create(Registries.ENCHANTMENT, MapPlus.id("foresight"));
	/** How far around the wearer the map is projected, in blocks. */
	private static final int REACH = 40;
	/** How see-through full ink is on the ground; watered down ink is fainter still. */
	private static final float GROUND_OPACITY = 0.55F;
	private static final float PILLAR_HEIGHT = 3.0F;
	private static final float PILLAR_HALF_WIDTH = 0.2F;
	private static final int FULL_BRIGHT = 0xF000F0;

	private Projection() {
	}

	public static void register() {
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(Projection::render);
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

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || !wearsForesight(player)) {
			return;
		}
		MapId mapId = heldMap(player);
		MapItemSavedData map = mapId == null ? null : level.getMapData(mapId);
		if (map == null || !map.dimension.equals(level.dimension())) {
			return;
		}
		byte[] ink = ClientInk.get(mapId.id());
		List<Mark> marks = ClientMarks.get(mapId);
		if (ink == null && marks.isEmpty()) {
			return;
		}

		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack pose = context.poseStack();
		pose.pushPose();
		pose.translate(-camera.x, -camera.y, -camera.z);

		int scale = MapPixels.scaleBlocks(map);
		int minX = Math.max(0, MapPixels.pixelX(map, player.getBlockX() - REACH));
		int maxX = Math.min(Ink.SIZE - 1, MapPixels.pixelX(map, player.getBlockX() + REACH));
		int minZ = Math.max(0, MapPixels.pixelZ(map, player.getBlockZ() - REACH));
		int maxZ = Math.min(Ink.SIZE - 1, MapPixels.pixelZ(map, player.getBlockZ() + REACH));
		if (ink != null && minX <= maxX && minZ <= maxZ) {
			context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (at, vertices) -> {
				for (int pz = minZ; pz <= maxZ; pz++) {
					for (int px = minX; px <= maxX; px++) {
						byte pixel = ink[px + pz * Ink.SIZE];
						if (!Ink.isPresent(pixel)) {
							continue;
						}
						float x0 = MapPixels.worldX(map, px);
						float z0 = MapPixels.worldZ(map, pz);
						int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x0 + scale / 2, (int) z0 + scale / 2);
						if (ground <= level.getMinY()) {
							continue;
						}
						float y = ground + 0.03F;
						int alpha = Math.round(GROUND_OPACITY * Ink.opacity(Ink.strength(pixel)) * 255);
						int color = alpha << 24 | Ink.dye(pixel).getMapColor().calculateARGBColor(Ink.brightness(pixel)) & 0xFFFFFF;
						vertices.addVertex(at, x0, y, z0).setColor(color);
						vertices.addVertex(at, x0, y, z0 + scale).setColor(color);
						vertices.addVertex(at, x0 + scale, y, z0 + scale).setColor(color);
						vertices.addVertex(at, x0 + scale, y, z0).setColor(color);
					}
				}
			});
		}

		Font font = minecraft.font;
		for (Mark mark : marks) {
			float x = MapPixels.worldX(map, mark.x()) + scale / 2.0F;
			float z = MapPixels.worldZ(map, mark.y()) + scale / 2.0F;
			if (Math.abs(x - player.getX()) > REACH || Math.abs(z - player.getZ()) > REACH) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
			if (ground <= level.getMinY()) {
				continue;
			}
			if (mark.isLabel()) {
				// Written across the ground, north up, as big as it is on the map.
				FormattedCharSequence text = Component.literal(mark.text()).getVisualOrderText();
				float size = Labels.fit(mark.text(), mark.size(), mark.x(), mark.y()).scale() * scale;
				pose.pushPose();
				pose.translate(x, ground + 0.06F, z);
				pose.mulPose(Axis.XP.rotationDegrees(90.0F));
				pose.scale(size, size, size);
				context.submitNodeCollector().submitText(pose, -font.width(text) / 2.0F, -4.0F, text, false, Font.DisplayMode.NORMAL, FULL_BRIGHT,
						MarkRenderer.inkColor(mark), 0, 0);
				pose.popPose();
			} else {
				int color = (mark.kind() == Mark.Kind.PIN ? 0xA0000000 | Labels.color(mark.color()) & 0xFFFFFF : MarkRenderer.inkColor(mark) & 0xFFFFFF | 0xA0000000);
				pillar(context, pose, x, ground, z, color);
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
