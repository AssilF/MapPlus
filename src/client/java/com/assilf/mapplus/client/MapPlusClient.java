package com.assilf.mapplus.client;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.client.atlas.AtlasScreen;
import com.assilf.mapplus.client.ink.InkScreen;
import com.assilf.mapplus.net.InkStatePayload;
import com.assilf.mapplus.net.OpenInkPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;

public class MapPlusClient implements ClientModInitializer {
	private static final int TABLE_WIDTH = 176;
	private static final int TABLE_HEIGHT = 166;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(InkStatePayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof InkScreen screen && screen.mapId() == payload.mapId()) {
				screen.accept(payload);
			}
		});

		// Right-clicking an atlas opens it; the server skips the vanilla book screen.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() && Atlas.isAtlas(player.getItemInHand(hand))) {
				Minecraft.getInstance().gui.setScreen(new AtlasScreen(hand));
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof CartographyTableScreen table) {
				addInkButton(client, table, width, height);
			}
		});
	}

	private static void addInkButton(Minecraft client, CartographyTableScreen table, int width, int height) {
		int left = (width - TABLE_WIDTH) / 2;
		int top = (height - TABLE_HEIGHT) / 2;
		Button button = Button.builder(Component.translatable("mapplus.ink.button"), b -> {
			MapId mapId = inputMap(table);
			if (mapId != null) {
				ClientPlayNetworking.send(new OpenInkPayload(mapId.id()));
				client.gui.setScreen(new InkScreen(table, mapId.id()));
			}
		}).bounds(left + TABLE_WIDTH + 4, top + 4, 40, 20).build();
		button.active = inputMap(table) != null;
		Screens.getWidgets(table).add(button);

		ScreenEvents.afterTick(table).register(screen -> button.active = inputMap(table) != null);
	}

	private static MapId inputMap(CartographyTableScreen table) {
		CartographyTableMenu menu = table.getMenu();
		ItemStack stack = menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem();
		return stack.is(Items.FILLED_MAP) ? stack.get(DataComponents.MAP_ID) : null;
	}
}
