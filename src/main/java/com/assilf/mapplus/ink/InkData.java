package com.assilf.mapplus.ink;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.MapPlusConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * All map ink, labels and pins in the world, keyed by map id, and who made
 * each map. Saved as {@code data/mapplus/ink.dat}, which vanilla never reads,
 * so removing the mod leaves every map as it was.
 */
public final class InkData extends SavedData {
	private static final int DATA_VERSION = 1;

	private static final Codec<InkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(data -> DATA_VERSION),
			Codec.unboundedMap(Codec.STRING, InkLayer.CODEC).optionalFieldOf("maps", Map.of()).forGetter(InkData::encodeLayers),
			Codec.unboundedMap(Codec.STRING, UUIDUtil.CODEC).optionalFieldOf("makers", Map.of()).forGetter(InkData::encodeMakers)
	).apply(instance, InkData::new));

	private static final SavedDataType<InkData> TYPE = new SavedDataType<>(
			MapPlus.id("ink"),
			InkData::new,
			CODEC,
			null
	);

	private final Map<Integer, InkLayer> layers = new HashMap<>();
	/** Who made each map, where known: whoever first used the empty map, or drew the blank canvas. */
	private final Map<Integer, UUID> makers = new HashMap<>();

	public InkData() {
	}

	private InkData(int dataVersion, Map<String, InkLayer> encoded, Map<String, UUID> encodedMakers) {
		encoded.forEach((key, layer) -> {
			try {
				layers.put(Integer.parseInt(key), layer);
			} catch (NumberFormatException e) {
				MapPlus.LOGGER.warn("Skipping ink for invalid map id {}", key);
			}
		});
		encodedMakers.forEach((key, maker) -> {
			try {
				makers.put(Integer.parseInt(key), maker);
			} catch (NumberFormatException e) {
				MapPlus.LOGGER.warn("Skipping the maker of invalid map id {}", key);
			}
		});
	}

	private Map<String, InkLayer> encodeLayers() {
		Map<String, InkLayer> encoded = new HashMap<>();
		layers.forEach((id, layer) -> {
			if (!layer.isBlank()) {
				encoded.put(Integer.toString(id), layer);
			}
		});
		return encoded;
	}

	private Map<String, UUID> encodeMakers() {
		Map<String, UUID> encoded = new HashMap<>();
		makers.forEach((id, maker) -> encoded.put(Integer.toString(id), maker));
		return encoded;
	}

	public UUID maker(MapId mapId) {
		return makers.get(mapId.id());
	}

	/** Records who made a map, unless it's already known. */
	public void setMaker(MapId mapId, UUID maker) {
		if (maker != null && makers.putIfAbsent(mapId.id(), maker) == null) {
			setDirty();
		}
	}

	public static InkData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** The ink on a map, or null if it has none. */
	public InkLayer find(MapId mapId) {
		return layers.get(mapId.id());
	}

	public InkLayer getOrCreate(MapId mapId) {
		return layers.computeIfAbsent(mapId.id(), id -> new InkLayer());
	}

	public boolean isSealed(MapId mapId) {
		InkLayer layer = find(mapId);
		return layer != null && layer.isSealed();
	}

	public void setSealed(MapId mapId, boolean sealed) {
		if (isSealed(mapId) != sealed) {
			getOrCreate(mapId).setSealed(sealed);
			setDirty();
		}
	}

	/** Seals a map's ink and marks, remembering who sealed it. */
	public void seal(MapId mapId, UUID sealer) {
		InkLayer layer = getOrCreate(mapId);
		layer.setSealed(true);
		layer.setSealer(sealer);
		setDirty();
	}

	/**
	 * Whether a player may scrape a map's seal off. With owned seals, only whoever
	 * sealed it, the map's maker or an operator may; seals from before seals had
	 * owners can be scraped by anyone.
	 */
	public boolean mayScrape(Player player, MapId mapId) {
		InkLayer layer = find(mapId);
		if (!MapPlusConfig.get().sealsAreOwned || layer == null || !layer.isSealed() || layer.sealer() == null) {
			return true;
		}
		UUID id = player.getUUID();
		return id.equals(layer.sealer()) || id.equals(maker(mapId)) || MapPlus.isOperator(player);
	}

	public void remove(MapId mapId) {
		if (layers.remove(mapId.id()) != null) {
			setDirty();
		}
	}
}
