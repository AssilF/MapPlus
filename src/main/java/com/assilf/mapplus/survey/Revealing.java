package com.assilf.mapplus.survey;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.mixin.EntityAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Revealing: looking through a spyglass with it, the living things in its
 * view glow, through walls, for whoever looks and no one else. Runs on the
 * server, telling only the looking player they glow, so it works for players
 * without the mod too.
 */
public final class Revealing {
	public static final ResourceKey<Enchantment> REVEALING = ResourceKey.create(Registries.ENCHANTMENT, MapPlus.id("revealing"));

	/** How far it reveals, in blocks, and how wide around the line of sight. */
	private static final double RANGE = 160.0;
	private static final double HALF_ANGLE_COS = Math.cos(Math.toRadians(6.0));
	/** How often what's in view is looked over, in ticks. */
	private static final int EVERY = 4;
	/** The glowing bit of an entity's shared flags. */
	private static final int GLOWING = 1 << 6;

	/** The entities each player is shown glowing. */
	private static final Map<UUID, Set<Integer>> SHOWN = new HashMap<>();

	private Revealing() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Revealing::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SHOWN.remove(handler.player.getUUID()));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Set<Integer> before = SHOWN.getOrDefault(player.getUUID(), Set.of());
			Set<Integer> now = player.isScoping() && FarView.has(player.getUseItem(), REVEALING) ? inView(player) : Set.of();
			for (int id : before) {
				Entity entity = player.level().getEntity(id);
				if (!now.contains(id) && entity != null) {
					send(player, entity, false);
				}
			}
			for (int id : now) {
				Entity entity = player.level().getEntity(id);
				if (entity != null) {
					send(player, entity, true);
				}
			}
			if (now.isEmpty()) {
				SHOWN.remove(player.getUUID());
			} else {
				SHOWN.put(player.getUUID(), now);
			}
		}
	}

	/** The living things within the spyglass's view. */
	private static Set<Integer> inView(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		AABB box = new AABB(eye, eye.add(look.scale(RANGE))).inflate(RANGE * 0.12);
		Set<Integer> seen = new HashSet<>();
		for (Entity entity : player.level().getEntities(player, box, entity -> entity instanceof LivingEntity && !entity.isSpectator())) {
			Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > 0 && distance <= RANGE && to.dot(look) / distance >= HALF_ANGLE_COS) {
				seen.add(entity.getId());
			}
		}
		return seen;
	}

	/** Tells one player an entity glows, or how it really is. */
	private static void send(ServerPlayer player, Entity entity, boolean glow) {
		byte flags = entity.getEntityData().get(EntityAccessor.mapplus$sharedFlags());
		byte shown = glow ? (byte) (flags | GLOWING) : flags;
		player.connection.send(new ClientboundSetEntityDataPacket(entity.getId(),
				List.of(SynchedEntityData.DataValue.create(EntityAccessor.mapplus$sharedFlags(), shown))));
	}
}
