package com.assilf.mapplus.nation;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.ink.InkData;
import com.assilf.mapplus.ink.InkLayer;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Nations, drawn from the banners players put up. A named banner placed in the
 * world is a border post of its design's nation; the land around it, as far as
 * a post reaches, is that nation's, and so is any land the banner walked the
 * bounds of while worn (see {@link Marches}). A map shows a nation's land once
 * the nation's banner is on it: marked by using the map on one of its posts,
 * as vanilla marks banners, or pinned at the cartography table. Players are
 * told when they cross into a nation. Borders are cartography only: nothing is
 * protected.
 */
public final class Nations {
	/** How often a map's territory is worked out again while the land under it is explored. */
	private static final int REFRESH_TICKS = 200;

	private record Cached(Territory territory, int postsVersion, int tick, Set<String> shown, Territory shownTerritory) {
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

	/** A banner's name as a nation's, or empty if it has none. */
	public static String name(ItemStack banner) {
		Component name = banner.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : Mark.clean(StringUtil.filterText(name.getString()));
	}

	/** A banner item's patterns, as marks and posts keep them. */
	public static List<Mark.Layer> layers(ItemStack banner) {
		List<Mark.Layer> layers = new ArrayList<>();
		for (BannerPatternLayers.Layer layer : banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers()) {
			layers.add(new Mark.Layer(layer.pattern().value().assetId().toString(), layer.color()));
		}
		return layers;
	}

	/** The design of a banner item, as its posts would have it. */
	public static String design(ItemStack banner) {
		return banner.getItem() instanceof BannerItem item ? Post.design(item.getColor(), layers(banner)) : "";
	}

	/**
	 * A banner was placed: a named one is a border post. If it walked the bounds
	 * of some land while worn, the post claims that land.
	 */
	public static void placed(ServerLevel level, BlockPos pos, AbstractBannerBlock banner, LivingEntity placer, ItemStack stack) {
		String text = name(stack);
		if (!MapPlusConfig.get().nations || text.isEmpty()) {
			return;
		}
		List<Mark.Layer> layers = layers(stack);
		NationData data = NationData.get(level.getServer());
		Claim claim = Claim.NONE;
		Bounds bounds = Bounds.read(stack);
		if (bounds != null && bounds.dimension().equals(level.dimension()) && bounds.name().equals(text) && bounds.count() > 1) {
			String design = Post.design(banner.getColor(), layers);
			List<Post> near = near(data, level.dimension(), bounds);
			claim = Claim.annex(bounds.points(), (x, z) -> holds(near, design, x, z, true), (x, z) -> holds(near, design, x, z, false));
		}
		Post post = new Post(level.dimension(), pos.immutable(), banner.getColor(), layers, text, claim);
		data.put(post);
		if (!claim.isEmpty()) {
			level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 0.8F);
			if (placer instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.translatableWithFallback("mapplus.nation.claimed", "%s claims %s blocks of land",
						nationName(data, post), String.format("%,d", claim.area())));
			}
		}
		if (MapPlusConfig.get().logMarks) {
			MapPlus.LOGGER.info("{} put up a border post of \"{}\" at {}{}", placer == null ? "Something" : placer.getName().getString(), text,
					pos.toShortString(), claim.isEmpty() ? "" : ", claiming " + claim.area() + " blocks of walked bounds");
		}
	}

	/** Whether a nation holds a spot by its posts' claims, or by being within a post's reach. */
	static boolean holds(NationData data, ResourceKey<Level> dimension, String design, int x, int z) {
		int reach = MapPlusConfig.get().postReach;
		for (Post post : data.posts()) {
			if (post.dimension().equals(dimension) && post.design().equals(design) && (post.claim().holds(x, z) || within(post, x, z, reach))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a spot is held, by claim or reach, by a nation (or, with
	 * {@code same} false, by any other nation).
	 */
	private static boolean holds(List<Post> posts, String design, int x, int z, boolean same) {
		int reach = MapPlusConfig.get().postReach;
		for (Post post : posts) {
			if (post.design().equals(design) == same && (post.claim().holds(x, z) || within(post, x, z, reach))) {
				return true;
			}
		}
		return false;
	}

	/** The posts whose land could reach a march's trail. */
	private static List<Post> near(NationData data, ResourceKey<Level> dimension, Bounds bounds) {
		int reach = MapPlusConfig.get().postReach + Claim.CELL * 4;
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int i = 0; i < bounds.count(); i++) {
			minX = Math.min(minX, bounds.x(i) - reach);
			minZ = Math.min(minZ, bounds.z(i) - reach);
			maxX = Math.max(maxX, bounds.x(i) + reach);
			maxZ = Math.max(maxZ, bounds.z(i) + reach);
		}
		List<Post> near = new ArrayList<>();
		for (Post post : data.posts()) {
			int x = post.pos().getX();
			int z = post.pos().getZ();
			if (post.dimension().equals(dimension)
					&& (x >= minX && x <= maxX && z >= minZ && z <= maxZ || post.claim().overlaps(minX, minZ, maxX, maxZ))) {
				near.add(post);
			}
		}
		return near;
	}

	private static boolean within(Post post, int x, int z, int reach) {
		double dx = post.pos().getX() - x;
		double dz = post.pos().getZ() - z;
		return dx * dx + dz * dz <= (double) reach * reach;
	}

	/**
	 * A map's territory, worked out again when posts change or now and then as
	 * the map is explored: the land of the nations whose banners are on the map.
	 */
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
			cached = new Cached(Territory.of(map, data.posts(), config.postReach), data.version(), now, null, Territory.NONE);
			TERRITORIES.put(mapId.id(), cached);
		}
		if (!config.bordersNeedBanners) {
			return cached.territory;
		}
		Set<String> shown = shown(server, data, mapId, map);
		if (!shown.equals(cached.shown)) {
			cached = new Cached(cached.territory, cached.postsVersion, cached.tick, shown, cached.territory.only(shown));
			TERRITORIES.put(mapId.id(), cached);
		}
		return cached.shownTerritory;
	}

	/**
	 * The nations a map shows, by design: those with a post marked on the map,
	 * as vanilla marks banners, and those whose banner is pinned on it.
	 */
	private static Set<String> shown(MinecraftServer server, NationData data, MapId mapId, MapItemSavedData map) {
		Set<String> shown = new HashSet<>();
		for (MapBanner banner : map.getBanners()) {
			for (Post post : data.posts()) {
				if (post.pos().equals(banner.pos()) && post.dimension().equals(map.dimension)) {
					shown.add(post.design());
				}
			}
		}
		InkLayer layer = InkData.get(server).find(mapId);
		if (layer != null) {
			for (Mark mark : layer.marks()) {
				if (mark.kind() == Mark.Kind.PIN) {
					shown.add(Post.design(mark.color(), mark.layers()));
				}
			}
		}
		return shown;
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

	/** The post whose nation holds a spot: the oldest whose walked bounds hold it, or else the nearest one in reach. */
	private static Post holder(ServerPlayer player, NationData data, int reach) {
		BlockPos at = player.blockPosition();
		for (Post post : data.posts()) {
			if (post.dimension().equals(player.level().dimension()) && post.claim().holds(at.getX(), at.getZ())) {
				return post;
			}
		}
		Post nearest = null;
		double best = (double) reach * reach;
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

	static Component nationName(NationData data, Post post) {
		return Component.literal(data.nameOf(post.design()))
				.withStyle(style -> style.withColor(TextColor.fromRgb(post.color().getTextColor())));
	}

	/** Forgets worked-out territories, as when the server stops. */
	public static void clear() {
		TERRITORIES.clear();
		INSIDE.clear();
	}
}
