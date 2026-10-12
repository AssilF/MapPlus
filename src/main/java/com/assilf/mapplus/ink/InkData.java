package com.assilf.mapplus.ink;

import com.assilf.mapplus.MapPlus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.HashMap;
import java.util.Map;

/**
 * All map ink in the world, keyed by map id. Saved as {@code data/mapplus/ink.dat},
 * which vanilla never reads, so removing the mod leaves every map as it was.
 */
public final class InkData extends SavedData {
	private static final int DATA_VERSION = 1;

	private static final Codec<InkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(data -> DATA_VERSION),
			Codec.unboundedMap(Codec.STRING, InkLayer.CODEC).optionalFieldOf("maps", Map.of()).forGetter(InkData::encodeLayers)
	).apply(instance, InkData::new));

	private static final SavedDataType<InkData> TYPE = new SavedDataType<>(
			MapPlus.id("ink"),
			InkData::new,
			CODEC,
			null
	);

	private final Map<Integer, InkLayer> layers = new HashMap<>();

	public InkData() {
	}

	private InkData(int dataVersion, Map<String, InkLayer> encoded) {
		encoded.forEach((key, layer) -> {
			try {
				layers.put(Integer.parseInt(key), layer);
			} catch (NumberFormatException e) {
				MapPlus.LOGGER.warn("Skipping ink for invalid map id {}", key);
			}
		});
	}

	private Map<String, InkLayer> encodeLayers() {
		Map<String, InkLayer> encoded = new HashMap<>();
		layers.forEach((id, layer) -> {
			if (layer.isSealed() || !layer.isEmpty()) {
				encoded.put(Integer.toString(id), layer);
			}
		});
		return encoded;
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

	public void remove(MapId mapId) {
		if (layers.remove(mapId.id()) != null) {
			setDirty();
		}
	}
}
