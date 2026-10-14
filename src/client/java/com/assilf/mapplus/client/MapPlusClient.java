package com.assilf.mapplus.client;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.client.atlas.AtlasBookScreen;
import com.assilf.mapplus.client.atlas.SketchBookScreen;
import com.assilf.mapplus.client.atlas.SketchPad;
import com.assilf.mapplus.client.desk.DeskState;
import com.assilf.mapplus.client.desk.DeskWing;
import com.assilf.mapplus.client.foresight.MapPlaces;
import com.assilf.mapplus.client.frame.FramePainter;
import com.assilf.mapplus.client.foresight.Projection;
import com.assilf.mapplus.client.ink.ClientInk;
import com.assilf.mapplus.client.marks.ClientMarks;
import com.assilf.mapplus.client.marks.MarkIcons;
import com.assilf.mapplus.client.mixin.AbstractContainerScreenAccessor;
import com.assilf.mapplus.client.scope.ScopeMap;
import com.assilf.mapplus.ink.DrawingDesk;
import com.assilf.mapplus.net.AtlasPagePayload;
import com.assilf.mapplus.net.InkLayerPayload;
import com.assilf.mapplus.net.InkStatePayload;
import com.assilf.mapplus.net.InkStrokePayload;
import com.assilf.mapplus.net.MapMarksPayload;
import com.assilf.mapplus.net.MapInkPayload;
import com.assilf.mapplus.net.MapPlacePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.world.InteractionHand;

import java.util.Map;
import java.util.WeakHashMap;

public class MapPlusClient implements ClientModInitializer {
	/** The drawing desk of each open cartography table, kept across window resizes. */
	private static final Map<Screen, DeskWing> WINGS = new WeakHashMap<>();

	@Override
	public void onInitializeClient() {
		MapPlus.serverHasMod = () -> ClientPlayNetworking.canSend(InkStrokePayload.TYPE);

		ClientPlayNetworking.registerGlobalReceiver(InkStatePayload.TYPE, (payload, context) -> {
			DeskState.accept(payload);
			if (context.client().gui.screen() instanceof CartographyTableScreen table && WINGS.get(table) != null) {
				WINGS.get(table).acknowledge(payload.ack());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(InkLayerPayload.TYPE, (payload, context) -> DeskState.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(MapMarksPayload.TYPE, (payload, context) -> ClientMarks.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(MapInkPayload.TYPE, (payload, context) -> ClientInk.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(MapPlacePayload.TYPE, (payload, context) -> MapPlaces.accept(payload));
		ScopeMap.register();
		FramePainter.register();
		Projection.register();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientMarks.clear();
			MapPlaces.clear();
			// Textures are released on the game's own thread; made again on the next server, from the resource packs in use then.
			client.execute(() -> {
				ClientInk.clear();
				MarkIcons.clear();
			});
		});

		// The table window widens for the drawing desk before vanilla lays it out.
		ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
			if (screen instanceof CartographyTableScreen table) {
				boolean desk = DrawingDesk.of(table.getMenu()).mapplus$isDesk();
				((AbstractContainerScreenAccessor) table).mapplus$setImageWidth(desk ? DeskWing.WIDTH : DeskWing.VANILLA_WIDTH);
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof CartographyTableScreen table && DrawingDesk.of(table.getMenu()).mapplus$isDesk()) {
				attachDesk(table);
			}
			if (screen instanceof AtlasBookScreen book) {
				ScreenEvents.remove(screen).register(closed -> leftOpen(book));
			}
			if (screen instanceof SketchBookScreen book && book.mapplus$sketchPad() != null) {
				attachSketchPad(screen, book.mapplus$sketchPad());
			}
		});
	}

	private static void attachSketchPad(Screen screen, SketchPad pad) {
		ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, delta) -> pad.extract(graphics, mouseX, mouseY));
		ScreenEvents.afterTick(screen).register(s -> pad.tick());
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !pad.mouseClicked(event.x(), event.y(), event.button()));
		ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> !pad.mouseDragged(event.x(), event.y(), event.button()));
		ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> !pad.mouseReleased(event.x(), event.y(), event.button()));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> !pad.mouseScrolled(mouseX, mouseY, vertical));
		ScreenEvents.remove(screen).register(s -> pad.finish());
	}

	private static void attachDesk(CartographyTableScreen table) {
		DeskWing wing = WINGS.get(table);
		if (wing == null) {
			wing = new DeskWing(table);
			WINGS.put(table, wing);
			DeskState.reset();
		}
		DeskWing desk = wing;
		ScreenEvents.afterBackground(table).register((screen, graphics, mouseX, mouseY, delta) -> desk.extractBackground(graphics, mouseX, mouseY));
		ScreenEvents.afterForeground(table).register((screen, graphics, mouseX, mouseY, delta) -> desk.extractForeground(graphics, mouseX, mouseY));
		ScreenEvents.afterTick(table).register(screen -> desk.tick());
		ScreenMouseEvents.allowMouseClick(table).register((screen, event) -> !desk.mouseClicked(event.x(), event.y(), event.button()));
		ScreenMouseEvents.allowMouseDrag(table).register((screen, event, dx, dy) -> !desk.mouseDragged(event.x(), event.y(), event.button()));
		ScreenMouseEvents.allowMouseRelease(table).register((screen, event) -> !desk.mouseReleased(event.x(), event.y(), event.button()));
		ScreenMouseEvents.allowMouseScroll(table).register((screen, mouseX, mouseY, horizontal, vertical) -> !desk.mouseScrolled(mouseX, mouseY, vertical));
		ScreenKeyboardEvents.allowKeyPress(table).register((screen, event) -> !desk.keyPressed(event));
		ScreenKeyboardEvents.allowCharType(table).register((screen, event) -> !desk.charTyped(event));
		ScreenEvents.remove(table).register(screen -> {
			desk.close();
			WINGS.remove(screen);
		});
	}

	/** Whether a drawing tool is carried over the desk's map, so it's drawn held by its tip. */
	public static boolean holdsToolOverMap(Screen screen, double mouseX, double mouseY) {
		DeskWing wing = WINGS.get(screen);
		return wing != null && wing.holdsToolOverMap(mouseX, mouseY);
	}

	/** Sends what's left of a pen stroke before the table window closes. */
	public static void beforeClose(Screen screen) {
		DeskWing wing = WINGS.get(screen);
		if (wing != null) {
			wing.close();
		}
	}

	/** A held atlas explores with the map it was closed on. */
	private static void leftOpen(AtlasBookScreen book) {
		InteractionHand hand = book.mapplus$atlasHand();
		if (hand != null && ClientPlayNetworking.canSend(AtlasPagePayload.TYPE)) {
			ClientPlayNetworking.send(new AtlasPagePayload(hand == InteractionHand.OFF_HAND, book.mapplus$atlasPage()));
		}
	}
}
