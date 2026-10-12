package com.assilf.mapplus.ink;

import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.mixin.MapItemSavedDataAccessor;
import com.assilf.mapplus.net.InkStatePayload;
import com.assilf.mapplus.net.InkStrokePayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

	private InkManager() {
	}

	/** What a drawing player has been told so far. */
	private static final class Session {
		int ack;
		InkStatePayload sent;
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
		Session session = session(player);
		session.ack = Math.max(session.ack, payload.seq());
		if (player.containerMenu instanceof CartographyTableMenu menu) {
			draw(player, menu, player.level(), payload);
		}
	}

	/** Applies a stroke to the map on a drawing desk, paying for it from the desk's second slot. */
	public static void draw(Player player, CartographyTableMenu menu, ServerLevel level, InkStrokePayload payload) {
		if (!DrawingDesk.of(menu).mapplus$isDesk()) {
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
			mapId = createCanvas(level);
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

	/** A blank map to draw on. Locked, so it never fills in with terrain. */
	private static MapId createCanvas(ServerLevel level) {
		MapId id = level.getFreeMapId();
		level.setMapData(id, MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, level.dimension()).locked());
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
				}
				continue;
			}
			Session session = session(player);
			MapId mapId = DrawingDesk.shownMap(menu);
			boolean sealed = false;
			if (mapId != null) {
				MapItemSavedData map = player.level().getMapData(mapId);
				if (map != null) {
					map.getHoldingPlayer(player);
					Packet<?> packet = map.getUpdatePacket(mapId, player);
					if (packet != null) {
						player.connection.send(packet);
					}
				}
				sealed = InkData.get(server).isSealed(mapId);
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
					isFree(player)
			);
			// Sent after the map, so the client has the new pixels when it learns the stroke is done.
			if (!state.equals(session.sent)) {
				ServerPlayNetworking.send(player, state);
				session.sent = state;
			}
		}
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
		if (layer == null || layer.isSealed() || layer.isEmpty()) {
			return false;
		}
		layer.clear();
		data.setDirty();

		MapItemSavedData map = server.overworld().getMapData(mapId);
		if (map != null) {
			markDirty(map, 0, 0, Ink.SIZE - 1, Ink.SIZE - 1);
		}
		return true;
	}

	/** Makes every player who sees this map receive these pixels again. */
	private static void markDirty(MapItemSavedData map, int minX, int minY, int maxX, int maxY) {
		MapItemSavedDataAccessor accessor = (MapItemSavedDataAccessor) map;
		accessor.mapplus$setColorsDirty(minX, minY);
		accessor.mapplus$setColorsDirty(maxX, maxY);
	}

	/**
	 * Pays for ink from the player's loaded supply, loading one more item from
	 * the table's second slot whenever it runs out.
	 */
	private static final class Wallet {
		private final Player player;
		private final InkMaterial material;
		private final Slot source;
		private final boolean free;
		private final InkSupply supply;
		private int charge;

		Wallet(Player player, InkMaterial material, Slot source) {
			this.player = player;
			this.material = material;
			this.source = source;
			this.free = isFree(player);
			this.supply = player.getAttachedOrElse(InkSupply.ATTACHMENT, InkSupply.EMPTY);
			this.charge = material.charge(supply);
		}

		boolean canAfford(int pixels) {
			if (free) {
				return true;
			}
			ItemStack stack = source.getItem();
			int items = material.equals(InkMaterial.of(stack)) ? stack.getCount() : 0;
			return (long) charge + (long) items * material.perItem() >= pixels;
		}

		boolean spend() {
			if (free) {
				return true;
			}
			if (charge <= 0) {
				ItemStack stack = source.getItem();
				if (!material.equals(InkMaterial.of(stack))) {
					return false;
				}
				if (material.kind() == InkMaterial.Kind.WATER) {
					source.set(new ItemStack(Items.GLASS_BOTTLE));
				} else {
					source.remove(1);
				}
				charge += material.perItem();
			}
			charge--;
			return true;
		}

		void save() {
			if (!free) {
				player.setAttached(InkSupply.ATTACHMENT, material.withCharge(supply, charge));
			}
		}
	}
}
