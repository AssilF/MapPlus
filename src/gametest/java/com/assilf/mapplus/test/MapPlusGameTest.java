package com.assilf.mapplus.test;

import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.InkData;
import com.assilf.mapplus.ink.InkLayer;
import com.assilf.mapplus.ink.InkManager;
import com.assilf.mapplus.survey.MapPixels;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class MapPlusGameTest {
	private static MapId newMap(ServerLevel level, BlockPos center) {
		ItemStack stack = MapItem.create(level, center.getX(), center.getZ(), (byte) 0, false, false);
		return stack.get(DataComponents.MAP_ID);
	}

	private static MapItemSavedData.MapPatch firstPatch(GameTestHelper helper, MapItemSavedData map, MapId mapId, Player player) {
		Packet<?> packet = map.getUpdatePacket(mapId, player);
		if (!(packet instanceof ClientboundMapItemDataPacket mapPacket) || mapPacket.colorPatch().isEmpty()) {
			throw helper.assertionException(Component.literal("Expected a map packet with colors"));
		}
		return mapPacket.colorPatch().get();
	}

	/** Ink shows up in the pixels sent to players, but never in the saved map. */
	@GameTest
	public void inkIsSentButNotSaved(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		MapItemSavedData map = level.getMapData(mapId);
		helper.assertTrue(map != null, Component.literal("Map data should exist"));

		int index = 5 + 7 * Ink.SIZE;
		byte ink = Ink.encode(DyeColor.RED, false);
		InkLayer layer = InkData.get(level.getServer()).getOrCreate(mapId);
		layer.set(index, ink);
		byte saved = map.colors[index];

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		MapItemSavedData.MapPatch patch = firstPatch(helper, map, mapId, player);
		int patchIndex = (5 - patch.startX()) + (7 - patch.startY()) * patch.width();
		helper.assertValueEqual(patch.mapColors()[patchIndex], Ink.toMapColor(ink), Component.literal("inked pixel in packet"));
		helper.assertValueEqual(map.colors[index], saved, Component.literal("saved map color"));
		helper.succeed();
	}

	/** Washing removes ink, and the next packet resends the map's own colors. */
	@GameTest
	public void washingRemovesInk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		MapItemSavedData map = level.getMapData(mapId);
		int index = 20 + 30 * Ink.SIZE;
		InkData.get(level.getServer()).getOrCreate(mapId).set(index, Ink.encode(DyeColor.BLUE, true));

		helper.assertTrue(InkManager.wash(level.getServer(), mapId), Component.literal("Wash should remove ink"));
		helper.assertFalse(InkManager.wash(level.getServer(), mapId), Component.literal("Second wash has nothing to remove"));

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		MapItemSavedData.MapPatch patch = firstPatch(helper, map, mapId, player);
		int patchIndex = (20 - patch.startX()) + (30 - patch.startY()) * patch.width();
		helper.assertValueEqual(patch.mapColors()[patchIndex], map.colors[index], Component.literal("washed pixel"));
		helper.succeed();
	}

	/** Sealed ink can't be washed off. */
	@GameTest
	public void sealedInkSurvivesWashing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		InkLayer layer = InkData.get(level.getServer()).getOrCreate(mapId);
		layer.set(0, Ink.encode(DyeColor.BLACK, false));
		layer.setSealed(true);
		helper.assertFalse(InkManager.wash(level.getServer(), mapId), Component.literal("Sealed ink should not wash off"));
		helper.assertTrue(Ink.isPresent(layer.get(0)), Component.literal("Sealed ink should remain"));
		helper.succeed();
	}

	/** Surveyed pixels use vanilla map colors for the block on top. */
	@GameTest
	public void surveyColorsMatchBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		MapId mapId = newMap(level, origin);
		MapItemSavedData map = level.getMapData(mapId);

		BlockPos column = helper.absolutePos(new BlockPos(1, 1, 1));
		int px = MapPixels.pixelX(map, column.getX());
		int pz = MapPixels.pixelZ(map, column.getZ());
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ());
		helper.setBlock(new BlockPos(1, top - origin.getY(), 1), Blocks.LAPIS_BLOCK);

		int color = MapPixels.color(level, MapPixels.ChunkLookup.loaded(level), map, px, pz, Double.NaN);
		helper.assertTrue(color >= 0, Component.literal("Chunk should be loaded"));
		helper.assertValueEqual(MapColor.byId(color >> 2), MapColor.LAPIS, Component.literal("surveyed color"));
		helper.succeed();
	}
}
