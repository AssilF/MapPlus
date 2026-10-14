package com.assilf.mapplus.ink;

import com.assilf.mapplus.MapPlusConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import com.assilf.mapplus.net.FramePaintPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Painting a map where it hangs: with a pen (a feather, or a brush for a wider
 * one) in one hand and ink in the other, using a framed map paints it instead
 * of turning it. Holding use keeps painting, each dab joined to the last into a
 * stroke; sneaking lays a lighter coat. Runs on the server, so players without
 * the mod can paint too.
 */
public final class FramePainting {
	/** Dabs this close together, in ticks, on the same frame are joined into one stroke. */
	private static final int JOIN_TICKS = 8;
	/** How often painting is heard, in ticks, after the stroke's first touch. */
	private static final int SOUND_TICKS = 16;

	private record Dab(int frame, int x, int y, int tick) {
	}

	private static final Map<UUID, Dab> LAST = new HashMap<>();
	private static final Map<UUID, Integer> HEARD = new HashMap<>();
	/** The tick each player last painted from the mod's own message, which comes at most once a tick. */
	private static final Map<UUID, Integer> SENT = new HashMap<>();

	/** An item frame, as painting needs to know it: whether it's fixed in place. */
	public interface Frame {
		boolean mapplus$isFixed();
	}

	private FramePainting() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(FramePaintPayload.TYPE, (payload, context) -> paintAimed(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LAST.remove(handler.player.getUUID());
			HEARD.remove(handler.player.getUUID());
			SENT.remove(handler.player.getUUID());
		});
	}

	/**
	 * Paints where a player with the mod aims at a framed map while holding use,
	 * once a tick at most, from close enough to touch it.
	 */
	public static void paintAimed(ServerPlayer player, FramePaintPayload payload) {
		int now = player.level().getServer().getTickCount();
		Integer sent = SENT.put(player.getUUID(), now);
		if (sent != null && sent == now) {
			return;
		}
		Entity entity = player.level().getEntity(payload.frame());
		if (!(entity instanceof ItemFrame frame) || !player.isWithinEntityInteractionRange(frame, 1.0)
				|| !paints(player, frame, ((Frame) frame).mapplus$isFixed())) {
			return;
		}
		paint(player.level(), player, frame, new Vec3(payload.x(), payload.y(), payload.z()));
	}

	/** The hand holding a pen, if the other holds ink, or null. */
	public static InteractionHand penHand(Player player) {
		for (InteractionHand hand : InteractionHand.values()) {
			InkTool tool = InkTool.of(player.getItemInHand(hand));
			InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			if ((tool == InkTool.PEN || tool == InkTool.BRUSH) && InkMaterial.of(player.getItemInHand(other)) != null) {
				return hand;
			}
		}
		return null;
	}

	/** Whether using a frame now paints the map in it, instead of turning it. */
	public static boolean paints(Player player, ItemFrame frame, boolean fixed) {
		return MapPlusConfig.get().framePainting && !fixed && !player.isSpectator() && frame.getItem().is(Items.FILLED_MAP)
				&& frame.getDirection().getAxis().isHorizontal() && penHand(player) != null;
	}

	/** Paints a framed map where it was used. */
	public static void paint(ServerLevel level, Player player, ItemFrame frame, Vec3 location) {
		// Painting needs what taking the map down would: build rights, and no spawn protection.
		if (!player.mayBuild() || !level.mayInteract(player, frame.blockPosition())) {
			return;
		}
		MapId mapId = frame.getItem().get(DataComponents.MAP_ID);
		MapItemSavedData map = mapId == null ? null : level.getMapData(mapId);
		InteractionHand penHand = penHand(player);
		int[] pixel = pixel(frame, location);
		if (map == null || penHand == null || pixel == null) {
			return;
		}
		InkData data = InkData.get(level.getServer());
		InkLayer layer = data.find(mapId);
		if (layer != null && layer.isSealed()) {
			player.sendOverlayMessage(Component.translatableWithFallback("mapplus.frame.sealed", "Sealed with wax"));
			return;
		}

		InkTool pen = InkTool.of(player.getItemInHand(penHand));
		InteractionHand inkHand = penHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		InkMaterial material = InkMaterial.of(player.getItemInHand(inkHand));
		int now = level.getServer().getTickCount();
		BitSet pixels = new BitSet(Ink.PIXELS);
		Dab last = LAST.get(player.getUUID());
		boolean joined = last != null && last.frame() == frame.getId() && now - last.tick() <= JOIN_TICKS;
		if (joined) {
			line(pixels, last.x(), last.y(), pixel[0], pixel[1], pen.size());
		} else {
			dab(pixels, pixel[0], pixel[1], pen.size());
			// A new stroke is heard at once.
			HEARD.remove(player.getUUID());
		}
		LAST.put(player.getUUID(), new Dab(frame.getId(), pixel[0], pixel[1], now));

		int strength = player.isShiftKeyDown() ? 2 : Ink.FULL;
		int quarters = material.quarters(strength);
		InkManager.Wallet wallet = new InkManager.Wallet(player, material, InkManager.InkSource.of(player, inkHand));
		if (layer == null) {
			layer = data.getOrCreate(mapId);
		}
		int credit = 0;
		int minX = Ink.SIZE;
		int minY = Ink.SIZE;
		int maxX = -1;
		int maxY = -1;
		for (int i = pixels.nextSetBit(0); i >= 0; i = pixels.nextSetBit(i + 1)) {
			byte ink = layer.get(i);
			byte next = material.apply(ink, strength);
			if (next == ink) {
				continue;
			}
			if (credit < quarters) {
				if (!wallet.spend()) {
					break;
				}
				credit += 4;
			}
			credit -= quarters;
			layer.set(i, next);
			minX = Math.min(minX, i % Ink.SIZE);
			minY = Math.min(minY, i / Ink.SIZE);
			maxX = Math.max(maxX, i % Ink.SIZE);
			maxY = Math.max(maxY, i / Ink.SIZE);
		}
		wallet.save();
		if (maxX >= 0) {
			data.setDirty();
			InkManager.markDirty(map, minX, minY, maxX, maxY);
			Integer heard = HEARD.get(player.getUUID());
			if (heard == null || now - heard >= SOUND_TICKS) {
				HEARD.put(player.getUUID(), now);
				level.playSound(null, frame.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.25F,
						1.5F + level.getRandom().nextFloat() * 0.3F);
			}
		}
	}

	/**
	 * The map pixel at a point on a frame on a wall, as the map hangs there:
	 * turned by the frame's rotation. The point is relative to the frame's
	 * middle, or in the world.
	 */
	public static int[] pixel(ItemFrame frame, Vec3 location) {
		Direction facing = frame.getDirection();
		if (!facing.getAxis().isHorizontal()) {
			return null;
		}
		Vec3 point = location.lengthSqr() < 4.0 ? location : location.subtract(frame.position());
		// Seen from in front, right is the facing turned counterclockwise.
		Direction right = facing.getCounterClockWise();
		double u = point.x * right.getStepX() + point.z * right.getStepZ() + 0.5;
		double v = 0.5 - point.y;
		for (int turn = frame.getRotation() % 4; turn > 0; turn--) {
			double was = u;
			u = v;
			v = 1.0 - was;
		}
		int x = (int) Math.floor(u * Ink.SIZE);
		int y = (int) Math.floor(v * Ink.SIZE);
		return new int[] {Math.clamp(x, 0, Ink.SIZE - 1), Math.clamp(y, 0, Ink.SIZE - 1)};
	}

	private static void dab(BitSet out, int x, int y, int size) {
		int from = -(size - 1) / 2;
		for (int dy = from; dy < from + size; dy++) {
			for (int dx = from; dx < from + size; dx++) {
				int px = x + dx;
				int py = y + dy;
				if (px >= 0 && py >= 0 && px < Ink.SIZE && py < Ink.SIZE) {
					out.set(px + py * Ink.SIZE);
				}
			}
		}
	}

	private static void line(BitSet out, int x0, int y0, int x1, int y1, int size) {
		int dx = Math.abs(x1 - x0);
		int dy = -Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int error = dx + dy;
		while (true) {
			dab(out, x0, y0, size);
			if (x0 == x1 && y0 == y1) {
				return;
			}
			int twice = 2 * error;
			if (twice >= dy) {
				error += dy;
				x0 += sx;
			}
			if (twice <= dx) {
				error += dx;
				y0 += sy;
			}
		}
	}
}
