package com.assilf.mapplus.client.scope;

import com.assilf.mapplus.MapPlus;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * While looking through a spyglass with a map in the other hand, the map shows
 * in the top right corner, so you can see it fill in as you survey.
 */
public final class ScopeMap {
	private static final Identifier MAP_BACKGROUND = Identifier.withDefaultNamespace("textures/map/map_background.png");
	private static final int MARGIN = 8;
	private static final int BORDER = 5;
	private static final MapRenderState STATE = new MapRenderState();

	private ScopeMap() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, MapPlus.id("scope_map"), ScopeMap::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !player.isScoping()) {
			return;
		}
		InteractionHand other = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack stack = player.getItemInHand(other);
		MapId mapId = stack.get(DataComponents.MAP_ID);
		MapItemSavedData data = mapId == null || !stack.is(Items.FILLED_MAP) ? null : minecraft.level.getMapData(mapId);
		if (data == null) {
			return;
		}
		int width = minecraft.getWindow().getGuiScaledWidth();
		int height = minecraft.getWindow().getGuiScaledHeight();
		int size = Math.clamp(Math.min(width, height) / 3, 64, 128);
		int x = width - MARGIN - BORDER - size;
		int y = MARGIN + BORDER;
		int framed = size + 2 * BORDER;
		graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_BACKGROUND, x - BORDER, y - BORDER, 0, 0, framed, framed, framed, framed);
		minecraft.getMapRenderer().extractRenderState(mapId, data, STATE);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(size / 128.0F, size / 128.0F);
		graphics.map(STATE);
		graphics.pose().popMatrix();
	}
}
