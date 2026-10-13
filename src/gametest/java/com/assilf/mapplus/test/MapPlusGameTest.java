package com.assilf.mapplus.test;

import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.ink.DrawingDesk;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.InkData;
import com.assilf.mapplus.ink.InkLayer;
import com.assilf.mapplus.ink.InkManager;
import com.assilf.mapplus.ink.InkSupply;
import com.assilf.mapplus.net.InkStrokePayload;
import com.assilf.mapplus.survey.MapPixels;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;

public class MapPlusGameTest {
	private static MapId newMap(ServerLevel level, BlockPos center) {
		ItemStack stack = MapItem.create(level, center.getX(), center.getZ(), (byte) 0, false, false);
		return stack.get(DataComponents.MAP_ID);
	}

	private static MapItemSavedData.MapPatch firstPatch(GameTestHelper helper, MapItemSavedData map, MapId mapId, Player player) {
		// Vanilla registers players as holders while they carry or see the map.
		map.getHoldingPlayer(player);
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

	private static ItemStack mapItem(MapId mapId) {
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.set(DataComponents.MAP_ID, mapId);
		return stack;
	}

	/** A cartography table as a player with the mod sees it. */
	private static CartographyTableMenu desk(GameTestHelper helper, Player player) {
		CartographyTableMenu menu = new CartographyTableMenu(1, player.getInventory(),
				ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
		DrawingDesk.of(menu).mapplus$setDesk(true);
		player.containerMenu = menu;
		return menu;
	}

	private static BitSet pixels(int... indices) {
		BitSet set = new BitSet(Ink.PIXELS);
		for (int index : indices) {
			set.set(index);
		}
		return set;
	}

	private static List<String> pages(ItemStack book) {
		return book.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY).getPages(false).toList();
	}

	/** All MapPlus mixins apply to this version of the game. */
	@GameTest
	public void mixinsApply(GameTestHelper helper) {
		MixinEnvironment.getCurrentEnvironment().audit();
		helper.succeed();
	}

	/** Players without the mod get the vanilla table: no canvases, no dyes, no books. */
	@GameTest
	public void vanillaPlayersGetTheVanillaTable(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = new CartographyTableMenu(1, player.getInventory(),
				ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
		helper.assertFalse(menu.getSlot(CartographyTableMenu.MAP_SLOT).mayPlace(new ItemStack(Items.MAP)), Component.literal("empty map in the first slot"));
		helper.assertFalse(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).mayPlace(new ItemStack(Items.DYE.pick(DyeColor.RED))), Component.literal("dye in the second slot"));
		helper.assertFalse(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).mayPlace(new ItemStack(Items.WRITABLE_BOOK)), Component.literal("book in the second slot"));
		helper.assertTrue(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).mayPlace(new ItemStack(Items.PAPER)), Component.literal("paper in the second slot"));
		helper.succeed();
	}

	/**
	 * Drawing on an empty map: the first stroke makes a canvas, the drawing is
	 * the result, ink is paid from the second slot, and taking the result uses
	 * up one empty map and nothing else.
	 */
	@GameTest
	public void emptyMapIsACanvas(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = desk(helper, player);
		helper.assertTrue(menu.getSlot(CartographyTableMenu.MAP_SLOT).mayPlace(new ItemStack(Items.MAP)), Component.literal("empty map in the first slot"));
		helper.assertTrue(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).mayPlace(new ItemStack(Items.DYE.pick(DyeColor.RED))), Component.literal("dye in the second slot"));

		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(new ItemStack(Items.MAP, 3));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DYE.pick(DyeColor.RED), 2));
		helper.assertTrue(menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem().isEmpty(), Component.literal("no result before drawing"));

		InkManager.draw(player, menu, level, new InkStrokePayload(1, InkStrokePayload.NEW_CANVAS, false, pixels(0, 1, 2, 129)));
		MapId canvas = DrawingDesk.of(menu).mapplus$canvas();
		helper.assertTrue(canvas != null, Component.literal("The first stroke should make a canvas"));
		ItemStack result = menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem();
		helper.assertValueEqual(result.get(DataComponents.MAP_ID), canvas, Component.literal("result shows the canvas"));
		MapItemSavedData canvasMap = level.getMapData(canvas);
		helper.assertTrue(canvasMap != null && canvasMap.locked, Component.literal("The canvas is a locked map"));
		InkLayer layer = InkData.get(level.getServer()).find(canvas);
		helper.assertValueEqual(layer.get(129), Ink.encode(DyeColor.RED, false), Component.literal("drawn pixel"));
		helper.assertValueEqual(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem().getCount(), 2, Component.literal("the dye in use stays until it runs out"));
		helper.assertValueEqual(player.getAttachedOrElse(InkSupply.ATTACHMENT, InkSupply.EMPTY).dye(DyeColor.RED),
				MapPlusConfig.get().inkPerItem - 4, Component.literal("ink left in the dye in use"));

		// A stroke aimed at a map that isn't on the table any more does nothing.
		InkManager.draw(player, menu, level, new InkStrokePayload(2, canvas.id() + 1000, false, pixels(5)));
		helper.assertFalse(Ink.isPresent(layer.get(5)), Component.literal("stale stroke ignored"));

		ItemStack taken = menu.getSlot(CartographyTableMenu.RESULT_SLOT).safeTake(1, Integer.MAX_VALUE, player);
		helper.assertValueEqual(taken.get(DataComponents.MAP_ID), canvas, Component.literal("taken drawing"));
		helper.assertValueEqual(menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem().getCount(), 2, Component.literal("empty maps after taking"));
		helper.assertValueEqual(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem().getCount(), 2, Component.literal("dyes after taking"));
		helper.assertTrue(menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem().isEmpty(), Component.literal("no second copy of the drawing"));
		helper.assertTrue(DrawingDesk.of(menu).mapplus$canvas() == null, Component.literal("the next drawing starts fresh"));
		helper.succeed();
	}

	/** Lines, boxes and fills are drawn whole or not at all; pen strokes stop when the ink runs out. */
	@GameTest
	public void shapesNeedEnoughInk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = desk(helper, player);
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(mapId));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.INK_SAC, 1));
		int perItem = MapPlusConfig.get().inkPerItem;

		BitSet tooMuch = new BitSet(Ink.PIXELS);
		tooMuch.set(0, perItem + 10);
		InkManager.draw(player, menu, level, new InkStrokePayload(1, mapId.id(), true, tooMuch));
		helper.assertTrue(InkData.get(level.getServer()).find(mapId) == null, Component.literal("an unaffordable shape draws nothing"));
		helper.assertValueEqual(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem().getCount(), 1, Component.literal("ink sac kept"));

		InkManager.draw(player, menu, level, new InkStrokePayload(2, mapId.id(), false, tooMuch));
		InkLayer layer = InkData.get(level.getServer()).find(mapId);
		helper.assertTrue(Ink.isPresent(layer.get(perItem - 1)), Component.literal("pen draws while ink lasts"));
		helper.assertFalse(Ink.isPresent(layer.get(perItem)), Component.literal("pen stops when ink runs out"));
		helper.assertTrue(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem().isEmpty(), Component.literal("ink sac used up"));
		helper.succeed();
	}

	/** Water erases ink and leaves a glass bottle; glow ink only brightens existing ink. */
	@GameTest
	public void waterErasesAndGlowBrightens(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = desk(helper, player);
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		InkLayer layer = InkData.get(level.getServer()).getOrCreate(mapId);
		layer.set(10, Ink.encode(DyeColor.BLUE, false));
		layer.set(11, Ink.encode(DyeColor.BLUE, false));
		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(mapId));

		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.GLOW_INK_SAC));
		InkManager.draw(player, menu, level, new InkStrokePayload(1, mapId.id(), false, pixels(10, 12)));
		helper.assertTrue(Ink.isGlow(layer.get(10)), Component.literal("ink glows"));
		helper.assertFalse(Ink.isPresent(layer.get(12)), Component.literal("glow alone draws nothing"));

		// A bottle with two pixels of water left.
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(PotionContents.createItemStack(Items.POTION, Potions.WATER));
		player.setAttached(InkSupply.ATTACHMENT, InkSupply.EMPTY.withWater(2));
		InkManager.draw(player, menu, level, new InkStrokePayload(2, mapId.id(), false, pixels(10, 11)));
		helper.assertFalse(Ink.isPresent(layer.get(10)), Component.literal("first pixel erased"));
		helper.assertFalse(Ink.isPresent(layer.get(11)), Component.literal("last of the water erased"));
		helper.assertTrue(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem().is(Items.GLASS_BOTTLE), Component.literal("bottle emptied"));
		helper.succeed();
	}

	/** Honeycomb seals a map at the table and stops drawing; an axe scrapes the seal off and is damaged, not used up. */
	@GameTest
	public void sealingAtTheTable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = desk(helper, player);
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		InkData data = InkData.get(level.getServer());

		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(mapId));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.HONEYCOMB, 2));
		helper.assertValueEqual(menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem().get(DataComponents.MAP_ID), mapId, Component.literal("seal result"));
		ItemStack sealedMap = menu.getSlot(CartographyTableMenu.RESULT_SLOT).safeTake(1, Integer.MAX_VALUE, player);
		helper.assertTrue(data.isSealed(mapId), Component.literal("sealed after taking"));
		helper.assertValueEqual(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem().getCount(), 1, Component.literal("honeycomb used"));
		helper.assertTrue(menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem().isEmpty(), Component.literal("map moved to the result"));

		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(sealedMap);
		helper.assertTrue(menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem().isEmpty(), Component.literal("a sealed map can't be sealed again"));

		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DYE.pick(DyeColor.RED)));
		InkManager.draw(player, menu, level, new InkStrokePayload(1, mapId.id(), false, pixels(3)));
		helper.assertFalse(Ink.isPresent(data.getOrCreate(mapId).get(3)), Component.literal("no drawing on a sealed map"));

		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.IRON_AXE));
		helper.assertValueEqual(menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem().get(DataComponents.MAP_ID), mapId, Component.literal("unseal result"));
		menu.getSlot(CartographyTableMenu.RESULT_SLOT).safeTake(1, Integer.MAX_VALUE, player);
		helper.assertFalse(data.isSealed(mapId), Component.literal("unsealed after taking"));
		ItemStack axe = menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem();
		helper.assertTrue(axe.is(Items.IRON_AXE) && axe.getDamageValue() == 1, Component.literal("axe damaged, not used up"));
		helper.assertTrue(menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem().isEmpty(), Component.literal("no second copy of the map"));
		helper.succeed();
	}

	/** Locking a map with a glass pane makes a new map; its ink and seal come along. */
	@GameTest
	public void lockingKeepsInk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = desk(helper, player);
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		InkData data = InkData.get(level.getServer());
		data.getOrCreate(mapId).set(40 + 50 * Ink.SIZE, Ink.encode(DyeColor.ORANGE, false));
		data.setSealed(mapId, true);

		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(mapId));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.GLASS_PANE));
		ItemStack locked = menu.getSlot(CartographyTableMenu.RESULT_SLOT).safeTake(1, Integer.MAX_VALUE, player);
		MapId lockedId = locked.get(DataComponents.MAP_ID);
		helper.assertTrue(lockedId != null && !lockedId.equals(mapId), Component.literal("locking makes a new map"));
		helper.assertValueEqual(data.find(lockedId).get(40 + 50 * Ink.SIZE), Ink.encode(DyeColor.ORANGE, false), Component.literal("ink on the locked map"));
		helper.assertTrue(data.isSealed(lockedId), Component.literal("seal on the locked map"));
		helper.succeed();
	}

	/** Zooming a map out with paper shrinks its ink into the bigger map. */
	@GameTest
	public void zoomingKeepsInk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = desk(helper, player);
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		InkData data = InkData.get(level.getServer());
		InkLayer layer = data.getOrCreate(mapId);
		for (int i = 0; i < Ink.SIZE; i++) {
			layer.set(i + 64 * Ink.SIZE, Ink.encode(DyeColor.BLUE, false));
		}

		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(mapId));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.PAPER));
		ItemStack zoomed = menu.getSlot(CartographyTableMenu.RESULT_SLOT).safeTake(1, Integer.MAX_VALUE, player);
		MapId zoomedId = zoomed.get(DataComponents.MAP_ID);
		helper.assertTrue(zoomedId != null && !zoomedId.equals(mapId), Component.literal("zooming makes a new map"));
		InkLayer shrunk = data.find(zoomedId);
		int inked = 0;
		for (int i = 0; i < Ink.PIXELS; i++) {
			if (Ink.isPresent(shrunk.get(i))) {
				inked++;
			}
		}
		helper.assertValueEqual(inked, Ink.SIZE / 2, Component.literal("a 128 pixel line becomes 64 pixels"));
		helper.succeed();
	}

	/** Shift-clicking a dye at the table puts it in the second slot. */
	@GameTest
	public void shiftClickPutsDyesInTheSecondSlot(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = desk(helper, player);
		int inventorySlot = CartographyTableMenu.RESULT_SLOT + 1;
		menu.getSlot(inventorySlot).set(new ItemStack(Items.DYE.pick(DyeColor.LIME), 5));
		menu.quickMoveStack(player, inventorySlot);
		helper.assertValueEqual(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem().getCount(), 5, Component.literal("dyes in the second slot"));
		helper.succeed();
	}

	/**
	 * Stitching adds the map as the book's next page. A book ending on a left
	 * page gets the map on the right, facing it; otherwise the map starts a new
	 * spread on the left with an empty notes page on the right.
	 */
	@GameTest
	public void stitchingAddsMapPages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		MapId first = newMap(level, origin);
		MapId second = newMap(level, origin.offset(512, 0, 0));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = desk(helper, player);

		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(first));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.WRITABLE_BOOK));
		ItemStack atlasStack = menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem().copy();
		Atlas atlas = Atlas.read(atlasStack).orElseThrow(() -> helper.assertionException(Component.literal("Result should be an atlas")));
		helper.assertValueEqual(atlas.plateAt(0).id(), first, Component.literal("first map on the left of the first spread"));
		helper.assertValueEqual(pages(atlasStack).size(), 2, Component.literal("map page and notes page"));
		helper.assertValueEqual(pages(atlasStack).get(1), "", Component.literal("empty notes page facing the map"));

		// The player writes on the notes page and starts the next spread.
		List<Filterable<String>> written = List.of(Filterable.passThrough(pages(atlasStack).get(0)),
				Filterable.passThrough("Village"), Filterable.passThrough("Mine"));
		atlasStack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(written));
		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(second));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(atlasStack);
		ItemStack bigger = menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem();
		Atlas biggerAtlas = Atlas.read(bigger).orElseThrow(() -> helper.assertionException(Component.literal("Second result should be an atlas")));
		helper.assertValueEqual(biggerAtlas.plateAt(3).id(), second, Component.literal("second map on the right, facing the last page"));
		helper.assertValueEqual(pages(bigger).subList(0, 3), pages(atlasStack), Component.literal("notes kept"));
		helper.assertValueEqual(biggerAtlas.mapInSpread(2).id(), second, Component.literal("the map faces page 3's partner"));

		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(first));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(bigger.copy());
		helper.assertTrue(menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem().isEmpty(),
				Component.literal("A map already in the atlas can't be stitched again"));
		helper.succeed();
	}

	/** Atlas data survives a write and read through the item's custom data. */
	@GameTest
	public void atlasDataRoundTrips(GameTestHelper helper) {
		ItemStack stack = new ItemStack(Items.WRITABLE_BOOK);
		Atlas atlas = new Atlas(List.of(new Atlas.Plate(7, 0, Optional.empty()), new Atlas.Plate(9, 3, Optional.empty())), 3);
		atlas.write(stack);
		Atlas read = Atlas.read(stack).orElseThrow(() -> helper.assertionException(Component.literal("Atlas should read back")));
		helper.assertValueEqual(read, atlas, Component.literal("atlas data"));
		helper.assertValueEqual(read.requiredPages(), 4, Component.literal("pages needed for both spreads"));
		helper.assertFalse(Atlas.isAtlas(new ItemStack(Items.WRITABLE_BOOK)), Component.literal("A plain book is not an atlas"));
		helper.succeed();
	}

	/** A stitched map keeps its item data, so names and explorer markers keep showing. */
	@GameTest
	public void stitchingKeepsTheMapItem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		ItemStack named = mapItem(mapId);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Village"));

		ItemStack atlasStack = Atlas.stitch(new ItemStack(Items.WRITABLE_BOOK), named, level.registryAccess());
		Atlas atlas = Atlas.read(atlasStack).orElseThrow(() -> helper.assertionException(Component.literal("Result should be an atlas")));
		ItemStack back = atlas.plates().getFirst().mapStack(level.registryAccess());
		helper.assertValueEqual(back.get(DataComponents.MAP_ID), mapId, Component.literal("map id"));
		helper.assertValueEqual(back.getHoverName().getString(), "Village", Component.literal("map name"));
		helper.assertValueEqual(pages(atlasStack).getFirst(), "Village", Component.literal("map page text without the mod"));
		helper.succeed();
	}
}
