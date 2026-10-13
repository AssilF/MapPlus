package com.assilf.mapplus.nation;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.ink.Mark;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nations, drawn from the banners players put up. A named banner placed in the
 * world is a border post of its design's nation; the land around it, as far as
 * a post reaches, is that nation's on every map, and players are told when they
 * cross into it. Borders are cartography only: nothing is protected.
 */
public final class Nations {
	/** How often a map's territory is worked out again while the land under it is explored. */
	private static final int REFRESH_TICKS = 200;

	private record Cached(Territory territory, int postsVersion, int tick) {
	}

	private static final Map<Integer, Cached> TERRITORIES = new HashMap<>();
	/** The nation each player was last told they're in: its design, and its name then, to say when they leave. */
	private record Inside(String design, Component name) {
	}

	private static final Map<UUID, Inside> INSIDE = new HashMap<>();

	private Nations() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Nations::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> INSIDE.remove(handler.player.getUUID()));
	}

	/** A banner was placed: a named one is a border post. */
	public static void placed(ServerLevel level, BlockPos pos, AbstractBannerBlock banner, LivingEntity placer, ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		if (!MapPlusConfig.get().nations || name == null) {
			return;
		}
		String text = Mark.clean(StringUtil.filterText(name.getString()));
		if (text.isEmpty()) {
			return;
		}
		List<Mark.Layer> layers = new ArrayList<>();
		for (BannerPatternLayers.Layer layer : stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers()) {
			layers.add(new Mark.Layer(layer.pattern().value().assetId().toString(), layer.color()));
		}
		NationData.get(level.getServer()).put(new Post(level.dimension(), pos.immutable(), banner.getColor(), layers, text));
		if (MapPlusConfig.get().logMarks) {
			MapPlus.LOGGER.info("{} put up a border post of \"{}\" at {}", placer == null ? "Something" : placer.getName().getString(), text,
					pos.toShortString());
		}
	}

	/** A map's territory, worked out again when posts change or now and then as the map is explored. */
	public static Territory territory(MinecraftServer server, MapId mapId, MapItemSavedData map) {
		MapPlusConfig config = MapPlusConfig.get();
		if (!config.nations || !config.nationsOnMaps) {
			return Territory.NONE;
		}
		NationData data = NationData.get(server);
		if (data.posts().isEmpty()) {
			return Territory.NONE;
		}
		int now = server.getTickCount();
		Cached cached = TERRITORIES.get(mapId.id());
		if (cached == null || cached.postsVersion != data.version() || now - cached.tick >= REFRESH_TICKS) {
			cached = new Cached(Territory.of(map, data.posts(), config.postReach), data.version(), now);
			TERRITORIES.put(mapId.id(), cached);
		}
		return cached.territory;
	}

	private static void tick(MinecraftServer server) {
		if (!MapPlusConfig.get().nations) {
			return;
		}
		int now = server.getTickCount();
		NationData data = NationData.get(server);
		if (now % 100 == 0) {
			takeDownFallenPosts(server, data);
		}
		if (now % 10 == 0 && MapPlusConfig.get().nationMessages) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tell(player, data);
			}
		}
	}

	/** Posts whose banner is gone, where it's loaded to see, are taken down. */
	private static void takeDownFallenPosts(MinecraftServer server, NationData data) {
		List<Post> fallen = new ArrayList<>();
		for (Post post : data.posts()) {
			ServerLevel level = server.getLevel(post.dimension());
			if (level == null) {
				fallen.add(post);
			} else if (level.isLoaded(post.pos()) && !(level.getBlockState(post.pos()).getBlock() instanceof AbstractBannerBlock)) {
				fallen.add(post);
			}
		}
		for (Post post : fallen) {
			data.remove(post.dimension(), post.pos());
		}
		if (TERRITORIES.size() > 4096) {
			TERRITORIES.clear();
		}
	}

	/** The post whose nation holds a spot: the nearest one in reach. */
	private static Post holder(ServerPlayer player, NationData data, int reach) {
		Post nearest = null;
		double best = (double) reach * reach;
		BlockPos at = player.blockPosition();
		for (Post post : data.posts()) {
			if (!post.dimension().equals(player.level().dimension())) {
				continue;
			}
			double dx = post.pos().getX() - at.getX();
			double dz = post.pos().getZ() - at.getZ();
			double distance = dx * dx + dz * dz;
			if (distance <= best) {
				best = distance;
				nearest = post;
			}
		}
		return nearest;
	}

	/** Tells a player in the action bar when they cross into a nation or out of one. */
	private static void tell(ServerPlayer player, NationData data) {
		Post post = holder(player, data, MapPlusConfig.get().postReach);
		Inside was = INSIDE.get(player.getUUID());
		if (post == null ? was == null : was != null && post.design().equals(was.design())) {
			return;
		}
		Component message;
		if (post != null) {
			Component name = nationName(data, post);
			INSIDE.put(player.getUUID(), new Inside(post.design(), name));
			message = Component.translatableWithFallback("mapplus.nation.entering", "Entering %s", name);
		} else {
			INSIDE.remove(player.getUUID());
			message = Component.translatableWithFallback("mapplus.nation.leaving", "Leaving %s", was.name());
		}
		player.sendOverlayMessage(message);
	}

	private static Component nationName(NationData data, Post post) {
		return Component.literal(data.nameOf(post.design()))
				.withStyle(style -> style.withColor(TextColor.fromRgb(post.color().getTextColor())));
	}

	/** Forgets worked-out territories, as when the server stops. */
	public static void clear() {
		TERRITORIES.clear();
		INSIDE.clear();
	}
}
