package com.assilf.mapplus.ink;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.mixin.MapItemSavedDataAccessor;
import com.assilf.mapplus.net.InkLayerPayload;
import com.assilf.mapplus.net.InkStatePayload;
import com.assilf.mapplus.net.InkStrokePayload;
import com.assilf.mapplus.survey.MapPixels;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of drawing at the cartography table: applies strokes, pays for
 * them from the table's second slot, and keeps each drawing player's view of
 * the canvas up to date.
 */
public final class InkManager {
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	/**
	 * A drawing on an empty map the player left at a table without an empty map
	 * to keep it on. It shows on the next empty map they put on a table.
	 */
	public static final AttachmentType<Integer> PENDING_CANVAS = AttachmentRegistry.create(
			MapPlus.id("pending_canvas"),
			builder -> builder.persistent(Codec.INT).copyOnDeath()
	);

	private InkManager() {
	}

	/** At most this many strokes and mark edits from one player are applied per tick. */
	private static final int EDITS_PER_TICK = 4;

	/** What a drawing player has been told so far. */
	private static final class Session {
		int ack;
		InkStatePayload sent;
		long tick;
		int edits;
		/** The map whose ink was last sent, and the version sent. */
		int layerMap = Integer.MIN_VALUE;
		int layerVersion;
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(InkStrokePayload.TYPE, (payload, context) ->
				handleStroke(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(InkManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUUID()));
	}

	private static Session session(ServerPlayer player) {
		return SESSIONS.computeIfAbsent(player.getUUID(), uuid -> new Session());
	}

	public static void handleStroke(ServerPlayer player, InkStrokePayload payload) {
		if (acknowledge(player, payload.seq()) && player.containerMenu instanceof CartographyTableMenu menu) {
			draw(player, menu, player.level(), payload);
		}
	}

	/**
	 * Notes a stroke or mark edit as handled, so the client stops showing it as
	 * pending. Returns false when the player sends more than a few a tick; those
	 * are dropped.
	 */
	static boolean acknowledge(ServerPlayer player, int seq) {
		Session session = session(player);
		session.ack = Math.max(session.ack, seq);
		long now = player.level().getGameTime();
		if (session.tick != now) {
			session.tick = now;
			session.edits = 0;
		}
		return ++session.edits <= EDITS_PER_TICK;
	}

	/** Applies a stroke to the map on a drawing desk, paying for it from the desk's second slot. */
	public static void draw(Player player, CartographyTableMenu menu, ServerLevel level, InkStrokePayload payload) {
		if (!DrawingDesk.of(menu).mapplus$isDesk()) {
			return;
		}
		// Only drawn with a tool on the cursor: a feather, brush, string, item frame, bucket or chain.
		InkTool tool = InkTool.of(menu.getCarried());
		if (tool == null || !tool.draws() || !DrawingDesk.mayUse(menu, tool)) {
			return;
		}
		Slot materialSlot = menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT);
		InkMaterial material = InkMaterial.of(materialSlot.getItem());
		if (material == null) {
			return;
		}

		// The stroke must be meant for the map on the table now, not one the client saw before.
		ItemStack input = menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem();
		MapId mapId;
		if (input.is(Items.FILLED_MAP)) {
			mapId = input.get(DataComponents.MAP_ID);
			if (mapId == null || mapId.id() != payload.mapId()) {
				return;
			}
		} else if (input.is(Items.MAP)) {
			mapId = DrawingDesk.of(menu).mapplus$canvas();
			if (mapId == null ? payload.mapId() != InkStrokePayload.NEW_CANVAS : payload.mapId() != mapId.id() && payload.mapId() != InkStrokePayload.NEW_CANVAS) {
				return;
			}
		} else {
			return;
		}

		InkData data = InkData.get(level.getServer());
		InkLayer layer = mapId == null ? null : data.find(mapId);
		if (layer != null && layer.isSealed()) {
			return;
		}

		BitSet pixels = payload.pixels();
		int cost = 0;
		for (int i = pixels.nextSetBit(0); i >= 0 && i < Ink.PIXELS; i = pixels.nextSetBit(i + 1)) {
			byte ink = layer == null ? Ink.NONE : layer.get(i);
			if (material.apply(ink) != ink) {
				cost++;
			}
		}
		Wallet wallet = new Wallet(player, material, materialSlot);
		if (cost == 0 || !wallet.canAfford(payload.shape() ? cost : 1)) {
			return;
		}

		if (mapId == null) {
			mapId = createCanvas(level, player);
			DrawingDesk.of(menu).mapplus$setCanvas(mapId);
		}
		if (layer == null) {
			layer = data.getOrCreate(mapId);
		}

		int minX = Ink.SIZE;
		int minY = Ink.SIZE;
		int maxX = -1;
		int maxY = -1;
		for (int i = pixels.nextSetBit(0); i >= 0 && i < Ink.PIXELS; i = pixels.nextSetBit(i + 1)) {
			byte ink = layer.get(i);
			byte next = material.apply(ink);
			if (next == ink) {
				continue;
			}
			if (!wallet.spend()) {
				break;
			}
			layer.set(i, next);
			int x = i % Ink.SIZE;
			int y = i / Ink.SIZE;
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
		}
		wallet.save();

		if (maxX >= 0) {
			data.setDirty();
			MapItemSavedData map = level.getMapData(mapId);
			if (map != null) {
				markDirty(map, minX, minY, maxX, maxY);
			}
		}
	}

	/** A blank map to draw on, made by this player. Locked, so it never fills in with terrain. */
	static MapId createCanvas(ServerLevel level, Player player) {
		MapId id = level.getFreeMapId();
		level.setMapData(id, MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, level.dimension()).locked());
		InkData.get(level.getServer()).setMaker(id, player.getUUID());
		return id;
	}

	/**
	 * Keeps players at a drawing desk in sync: the map on the canvas and what the
	 * desk shows about it.
	 */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.containerMenu instanceof CartographyTableMenu menu) || !DrawingDesk.of(menu).mapplus$isDesk()) {
				Session session = SESSIONS.get(player.getUUID());
				if (session != null) {
					session.sent = null;
					session.layerMap = Integer.MIN_VALUE;
				}
				continue;
			}
			Session session = session(player);
			MapId mapId = DrawingDesk.shownMap(menu);
			boolean sealed = false;
			boolean scrapable = true;
			if (mapId != null) {
				MapItemSavedData map = player.level().getMapData(mapId);
				if (map != null) {
					MapViewers.view(player, mapId, map);
				}
				InkData data = InkData.get(server);
				sealed = data.isSealed(mapId);
				scrapable = data.mayScrape(player, mapId);
			}

			InkMaterial material = InkMaterial.of(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem());
			InkSupply supply = player.getAttachedOrElse(InkSupply.ATTACHMENT, InkSupply.EMPTY);
			MapId canvas = DrawingDesk.of(menu).mapplus$canvas();
			InkStatePayload state = new InkStatePayload(
					session.ack,
					canvas == null ? -1 : canvas.id(),
					sealed,
					material == null ? 0 : material.charge(supply),
					material == null ? 1 : material.perItem(),
					isFree(player),
					DrawingDesk.mayMark(menu),
					scrapable
			);
			sendLayer(player, session, mapId, canvas);
			// Sent after the map, so the client has the new pixels when it learns the stroke is done.
			if (!state.equals(session.sent)) {
				ServerPlayNetworking.send(player, state);
				session.sent = state;
			}
		}
	}

	/** Sends the ink on the desk's map when the map or its ink changes, for the fill tool. */
	private static void sendLayer(ServerPlayer player, Session session, MapId mapId, MapId canvas) {
		int id = mapId != null ? mapId.id() : canvas == null ? InkStrokePayload.NEW_CANVAS : canvas.id();
		InkLayer layer = mapId == null ? null : InkData.get(player.level().getServer()).find(mapId);
		int version = layer == null ? 0 : layer.inkVersion();
		if (session.layerMap != id || session.layerVersion != version) {
			session.layerMap = id;
			session.layerVersion = version;
			ServerPlayNetworking.send(player, new InkLayerPayload(id, layer == null ? new byte[Ink.PIXELS] : layer.pixels().clone()));
		}
	}

	/** Pixels a stack can still draw, when its top item has {@code charge} left (0 meaning untouched). */
	public static long available(int items, int charge, int perItem) {
		if (items <= 0) {
			return 0;
		}
		return (charge > 0 ? charge : perItem) + (long) (items - 1) * perItem;
	}

	private static boolean isFree(Player player) {
		return player.isCreative() && MapPlusConfig.get().creativeInkIsFree;
	}

	/**
	 * Washes all ink off a map, as when it is dipped into a water cauldron.
	 *
	 * @return whether there was ink to wash off
	 */
	public static boolean wash(MinecraftServer server, MapId mapId) {
		InkData data = InkData.get(server);
		InkLayer layer = data.find(mapId);
		if (layer == null || layer.isSealed() || !layer.hasInk()) {
			return false;
		}
		layer.clear();
		data.setDirty();

		MapItemSavedData map = server.overworld().getMapData(mapId);
		if (map != null) {
			markDirty(map, 0, 0, Ink.SIZE - 1, Ink.SIZE - 1);
			marksChanged(map);
		}
		return true;
	}

	/**
	 * Brings a map's ink and seal over to the new map made by locking it (same
	 * area, copied as is) or zooming it out (twice the area, each 2 by 2 block
	 * of ink shrinks into one pixel of the new map).
	 */
	public static void carryInk(ServerLevel level, MapId from, MapId to) {
		InkData data = InkData.get(level.getServer());
		data.setMaker(to, data.maker(from));
		InkLayer source = data.find(from);
		MapItemSavedData oldMap = level.getMapData(from);
		MapItemSavedData newMap = level.getMapData(to);
		if (source == null || oldMap == null || newMap == null) {
			return;
		}
		InkLayer target = data.getOrCreate(to);
		target.setSealed(source.isSealed());
		target.setSealer(source.sealer());
		int oldScale = MapPixels.scaleBlocks(oldMap);
		for (Mark mark : source.marks()) {
			int x = MapPixels.pixelX(newMap, MapPixels.worldX(oldMap, mark.x()) + oldScale / 2);
			int y = MapPixels.pixelZ(newMap, MapPixels.worldZ(oldMap, mark.y()) + oldScale / 2);
			if (x >= 0 && y >= 0 && x < Ink.SIZE && y < Ink.SIZE) {
				target.putMark(mark.at(x, y));
			}
		}
		marksChanged(newMap);
		for (int i = 0; i < Ink.PIXELS; i++) {
			byte ink = source.get(i);
			if (!Ink.isPresent(ink)) {
				continue;
			}
			int x = MapPixels.pixelX(newMap, MapPixels.worldX(oldMap, i % Ink.SIZE) + oldScale / 2);
			int y = MapPixels.pixelZ(newMap, MapPixels.worldZ(oldMap, i / Ink.SIZE) + oldScale / 2);
			if (x >= 0 && y >= 0 && x < Ink.SIZE && y < Ink.SIZE && !Ink.isPresent(target.get(x + y * Ink.SIZE))) {
				target.set(x + y * Ink.SIZE, ink);
			}
		}
		data.setDirty();
		markDirty(newMap, 0, 0, Ink.SIZE - 1, Ink.SIZE - 1);
	}

	/** Makes every player who sees this map receive its markers again, and with them its labels and pins. */
	static void marksChanged(MapItemSavedData map) {
		((MapItemSavedDataAccessor) map).mapplus$setDecorationsDirty();
	}

	/** Makes every player who sees this map receive these pixels again. */
	private static void markDirty(MapItemSavedData map, int minX, int minY, int maxX, int maxY) {
		MapItemSavedDataAccessor accessor = (MapItemSavedDataAccessor) map;
		accessor.mapplus$setColorsDirty(minX, minY);
		accessor.mapplus$setColorsDirty(maxX, maxY);
	}

	/**
	 * Pays for ink with the items in the table's second slot. The item on top
	 * stays in the slot until its ink is used up, like a tool wearing down; the
	 * ink left in it is kept on the player, so taking the stack out and putting
	 * it back loses nothing.
	 */
	static final class Wallet {
		private final Player player;
		private final InkMaterial material;
		private final Slot source;
		private final boolean free;
		private final InkSupply supply;
		/** Ink left in the top item, or 0 if it hasn't been started. */
		private int charge;

		Wallet(Player player, InkMaterial material, Slot source) {
			this.player = player;
			this.material = material;
			this.source = source;
			this.free = isFree(player);
			this.supply = player.getAttachedOrElse(InkSupply.ATTACHMENT, InkSupply.EMPTY);
			this.charge = Math.min(material.charge(supply), material.perItem());
		}

		boolean canAfford(int pixels) {
			if (free) {
				return true;
			}
			ItemStack stack = source.getItem();
			int items = material.equals(InkMaterial.of(stack)) ? stack.getCount() : 0;
			return available(items, charge, material.perItem()) >= pixels;
		}

		boolean spend() {
			if (free) {
				return true;
			}
			if (!material.equals(InkMaterial.of(source.getItem()))) {
				return false;
			}
			if (charge <= 0) {
				charge = material.perItem();
			}
			charge--;
			if (charge == 0) {
				useUpItem();
			}
			return true;
		}

		private void useUpItem() {
			source.remove(1);
			if (material.kind() == InkMaterial.Kind.WATER) {
				// The emptied bottle takes the water bottle's place, or goes to the player.
				ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
				if (source.getItem().isEmpty()) {
					source.set(bottle);
				} else if (!player.getInventory().add(bottle)) {
					Block.popResource(player.level(), player.blockPosition(), bottle);
				}
			}
		}

		void save() {
			if (!free) {
				player.setAttached(InkSupply.ATTACHMENT, material.withCharge(supply, charge));
			}
		}
	}
}
