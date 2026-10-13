package com.assilf.mapplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server and client settings, stored in {@code config/mapplus.json}.
 */
public final class MapPlusConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Ink gained from one ink sac, dye or glow ink sac. */
	public int inkPerItem = 256;
	/** Pixels erased by one water bottle. */
	public int waterPerBottle = 256;
	/** Drawing is free in creative mode. */
	public boolean creativeInkIsFree = true;

	/** Labels, pins and borders need a loom next to the cartography table. */
	public boolean markingNeedsLoom = true;
	/** Labels one map can carry. */
	public int maxLabelsPerMap = 32;
	/** Pins and icons one map can carry. */
	public int maxPinsPerMap = 32;
	/** Only whoever sealed a map, its maker or an operator can scrape the seal off. */
	public boolean sealsAreOwned = true;
	/** Log every label and pin placed, with who placed it. */
	public boolean logMarks = true;

	/** Whether looking through a spyglass while holding a map fills the map in. */
	public boolean spyglassSurvey = true;
	/** Map pixels the spyglass can fill per tick, per player. */
	public int surveyPixelsPerTick = 48;
	/** How far the spyglass surveys, as a multiple of the server view distance. */
	public double surveyRangeMultiplier = 2.0;
	/** Whether the spyglass may generate chunks nobody has visited yet. */
	public boolean spyglassGeneratesChunks = true;
	/** Chunks the spyglass may load or generate per tick, per player. */
	public int surveyChunksPerTick = 2;

	/** Whether named banners put up in the world are nations' border posts. */
	public boolean nations = true;
	/** How far a border post holds land, in blocks. */
	public int postReach = 48;
	/** Whether maps show nations' borders and land. */
	public boolean nationsOnMaps = true;
	/**
	 * Whether a map shows a nation's land only once the nation's banner is on it:
	 * marked by using the map on one of its posts, or pinned at the table.
	 */
	public boolean bordersNeedBanners = true;
	/**
	 * Whether named banners can be worn on the head to walk the bounds of land,
	 * and claim it when planted. Experimental: off until it's reworked.
	 */
	public boolean walkingBounds = false;
	/** How far a worn named banner remembers the bounds it walks, in blocks. */
	public int boundsLength = 2048;
	/** Whether players are told in the action bar when they cross a border. */
	public boolean nationMessages = true;

	/** Whether a book and quill can be sketched in, holding ink in the other hand. */
	public boolean sketching = true;
	/** How many pages of one book can hold sketches. */
	public int sketchPages = 16;

	/** Whether a map in an item frame can be painted where it hangs, with a pen in one hand and ink in the other. */
	public boolean framePainting = true;

	/** Whether an empty map used underground is a cave map, drawing what its holder sees around them. */
	public boolean caveMaps = true;
	/** How far around its holder a cave map draws what they can see, in blocks. */
	public int caveMapReach = 20;
	/** How far an amethyst shard's ring reveals the land on a held map, in blocks; an echo shard's reaches twice as far. */
	public int echoReach = 24;
	/** Ticks before a shard can ring again. */
	public int echoCooldown = 40;

	private static MapPlusConfig instance = new MapPlusConfig();

	public static MapPlusConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("mapplus.json");
		MapPlusConfig config = new MapPlusConfig();

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				MapPlusConfig read = GSON.fromJson(reader, MapPlusConfig.class);
				if (read != null) {
					config = read;
				}
			} catch (IOException | RuntimeException e) {
				MapPlus.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}

		config.inkPerItem = Math.max(1, config.inkPerItem);
		config.waterPerBottle = Math.max(1, config.waterPerBottle);
		config.surveyPixelsPerTick = Math.max(0, config.surveyPixelsPerTick);
		config.surveyRangeMultiplier = Math.max(1.0, config.surveyRangeMultiplier);
		config.surveyChunksPerTick = Math.max(0, config.surveyChunksPerTick);
		config.postReach = Math.clamp(config.postReach, 1, 1024);
		// A march no longer than this closes in no more land than one claim can hold.
		config.boundsLength = Math.clamp(config.boundsLength, 0, 4096);
		config.caveMapReach = Math.clamp(config.caveMapReach, 1, 64);
		config.echoReach = Math.clamp(config.echoReach, 1, 64);
		config.echoCooldown = Math.max(0, config.echoCooldown);
		config.sketchPages = Math.clamp(config.sketchPages, 0, 50);
		// Together they stay within what one map's marks packet can carry.
		config.maxLabelsPerMap = Math.clamp(config.maxLabelsPerMap, 0, 512);
		config.maxPinsPerMap = Math.clamp(config.maxPinsPerMap, 0, 512);
		instance = config;

		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			MapPlus.LOGGER.warn("Could not write {}", path, e);
		}
	}
}
