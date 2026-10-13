package com.assilf.mapplus.test;

import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.atlas.Sketch;
import com.assilf.mapplus.ink.Sketching;
import com.assilf.mapplus.nation.Bounds;
import com.assilf.mapplus.nation.Claim;
import com.assilf.mapplus.nation.Marches;
import com.assilf.mapplus.nation.NationData;
import com.assilf.mapplus.nation.Nations;
import com.assilf.mapplus.net.SketchPayload;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.AbstractBannerBlock;
import com.assilf.mapplus.ink.DrawingDesk;
import com.assilf.mapplus.ink.FramePainting;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.InkData;
import com.assilf.mapplus.ink.InkLayer;
import com.assilf.mapplus.ink.InkManager;
import com.assilf.mapplus.ink.InkMaterial;
import com.assilf.mapplus.ink.InkSupply;
import com.assilf.mapplus.ink.Mark;
import com.assilf.mapplus.ink.MarkManager;
import com.assilf.mapplus.nation.Post;
import com.assilf.mapplus.nation.Territory;
import com.assilf.mapplus.net.InkStrokePayload;
import com.assilf.mapplus.net.MarkEditPayload;
import com.assilf.mapplus.survey.CaveMaps;
import com.assilf.mapplus.survey.MapPixels;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
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
import net.minecraft.world.phys.Vec3;
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

	/** Each wash waters ink down a step; four wash full ink off, and the next packet resends the map's own colors. */
	@GameTest
	public void washingFadesInk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		MapItemSavedData map = level.getMapData(mapId);
		int index = 20 + 30 * Ink.SIZE;
		InkLayer layer = InkData.get(level.getServer()).getOrCreate(mapId);
		layer.set(index, Ink.encode(DyeColor.BLUE, true));

		helper.assertTrue(InkManager.wash(level.getServer(), mapId), Component.literal("Wash should water ink down"));
		helper.assertValueEqual(Ink.strength(layer.get(index)), 1, Component.literal("strength after one wash"));
		helper.assertTrue(Ink.isGlow(layer.get(index)) && Ink.dye(layer.get(index)) == DyeColor.BLUE, Component.literal("still glowing blue"));
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(InkManager.wash(level.getServer(), mapId), Component.literal("Wash " + (i + 2) + " should water ink down"));
		}
		helper.assertFalse(Ink.isPresent(layer.get(index)), Component.literal("Four washes take full ink off"));
		helper.assertFalse(InkManager.wash(level.getServer(), mapId), Component.literal("Fifth wash has nothing to remove"));

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
		// Drawing is done with a tool on the cursor.
		menu.setCarried(new ItemStack(Items.FEATHER));
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
		data.getOrCreate(mapId).putMark(new Mark(1, Mark.Kind.LABEL, 40, 50, DyeColor.ORANGE, "Mill", Mark.MEDIUM, Optional.empty()));
		data.setSealed(mapId, true);

		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(mapId));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.GLASS_PANE));
		ItemStack locked = menu.getSlot(CartographyTableMenu.RESULT_SLOT).safeTake(1, Integer.MAX_VALUE, player);
		MapId lockedId = locked.get(DataComponents.MAP_ID);
		helper.assertTrue(lockedId != null && !lockedId.equals(mapId), Component.literal("locking makes a new map"));
		helper.assertValueEqual(data.find(lockedId).get(40 + 50 * Ink.SIZE), Ink.encode(DyeColor.ORANGE, false), Component.literal("ink on the locked map"));
		helper.assertTrue(data.isSealed(lockedId), Component.literal("seal on the locked map"));
		helper.assertValueEqual(data.find(lockedId).marks().size(), 1, Component.literal("label on the locked map"));
		helper.succeed();
	}

	/** Strokes need a drawing tool on the cursor; the iron chain's borders also need a loom next to the table. */
	@GameTest
	public void drawingNeedsATool(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = desk(helper, player);
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		InkData data = InkData.get(level.getServer());
		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(mapId));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.INK_SAC, 4));

		menu.setCarried(ItemStack.EMPTY);
		InkManager.draw(player, menu, level, new InkStrokePayload(1, mapId.id(), false, pixels(1)));
		helper.assertTrue(data.find(mapId) == null || !Ink.isPresent(data.find(mapId).get(1)), Component.literal("nothing drawn without a tool"));

		menu.setCarried(new ItemStack(Items.IRON_CHAIN));
		InkManager.draw(player, menu, level, new InkStrokePayload(2, mapId.id(), true, pixels(2)));
		helper.assertTrue(data.find(mapId) == null || !Ink.isPresent(data.find(mapId).get(2)), Component.literal("no border without a loom"));

		helper.setBlock(new BlockPos(1, 0, 0), Blocks.LOOM);
		InkManager.draw(player, menu, level, new InkStrokePayload(3, mapId.id(), true, pixels(3)));
		helper.assertTrue(Ink.isPresent(data.find(mapId).get(3)), Component.literal("border with a loom next to the table"));
		helper.succeed();
	}

	/**
	 * Labels are written with a sign, in the second slot's dye, and cost ink for
	 * their letters; pins take a banner's color and name. Players without the
	 * mod see both as named banner markers.
	 */
	@GameTest
	public void labelsAndPins(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = desk(helper, player);
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		InkData data = InkData.get(level.getServer());
		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(mapId));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DYE.pick(DyeColor.RED), 1));
		menu.setCarried(new ItemStack(Items.OAK_SIGN));

		MarkEditPayload write = new MarkEditPayload(1, mapId.id(), MarkEditPayload.Action.WRITE, -1, 30, 40, Mark.MEDIUM, "Base \u00a7ccamp");
		helper.assertFalse(MarkManager.edit(player, menu, level, write), Component.literal("no labels without a loom"));
		helper.setBlock(new BlockPos(0, 0, 1), Blocks.LOOM);
		helper.assertTrue(MarkManager.edit(player, menu, level, write), Component.literal("label written"));
		Mark label = data.find(mapId).marks().getFirst();
		helper.assertTrue(label.isLabel() && label.color() == DyeColor.RED && !label.text().contains("\u00a7"), Component.literal("a red label, without formatting codes"));
		InkMaterial red = new InkMaterial(InkMaterial.Kind.DYE, DyeColor.RED);
		int afterWriting = red.charge(player.getAttachedOrElse(InkSupply.ATTACHMENT, InkSupply.EMPTY));
		helper.assertValueEqual(afterWriting, red.perItem() - Mark.cost(label.text(), Mark.MEDIUM), Component.literal("ink left after writing"));

		MarkEditPayload rewrite = new MarkEditPayload(2, mapId.id(), MarkEditPayload.Action.REWRITE, label.id(), 0, 0, Mark.MEDIUM, label.text() + " north");
		helper.assertTrue(MarkManager.edit(player, menu, level, rewrite), Component.literal("label rewritten"));
		int afterRewriting = red.charge(player.getAttachedOrElse(InkSupply.ATTACHMENT, InkSupply.EMPTY));
		helper.assertValueEqual(afterWriting - afterRewriting, Mark.cost("north", Mark.MEDIUM), Component.literal("rewriting pays only for the added letters"));

		ItemStack banner = new ItemStack(Items.BANNER.pick(DyeColor.BLUE));
		banner.set(DataComponents.CUSTOM_NAME, Component.literal("Town hall"));
		menu.setCarried(banner);
		helper.assertTrue(MarkManager.edit(player, menu, level, new MarkEditPayload(3, mapId.id(), MarkEditPayload.Action.PIN, -1, 64, 64, 0, "")),
				Component.literal("banner pinned"));
		Mark pin = data.find(mapId).marks().get(1);
		helper.assertTrue(!pin.isLabel() && pin.color() == DyeColor.BLUE && pin.text().equals("Town hall"), Component.literal("a blue pin named after its banner"));

		MapItemSavedData map = level.getMapData(mapId);
		Player vanilla = helper.makeMockPlayer(GameType.SURVIVAL);
		map.getHoldingPlayer(vanilla);
		Packet<?> packet = map.getUpdatePacket(mapId, vanilla);
		List<String> names = packet instanceof ClientboundMapItemDataPacket mapPacket
				? mapPacket.decorations().orElse(List.of()).stream().flatMap(decoration -> decoration.name().stream()).map(Component::getString).toList()
				: List.of();
		helper.assertTrue(names.contains("Town hall") && names.contains(label.text() + " north"), Component.literal("pins and labels as named banner markers, got " + names));

		helper.assertFalse(MarkManager.edit(player, menu, level, new MarkEditPayload(4, mapId.id(), MarkEditPayload.Action.MOVE, label.id(), 10, 10, 0, "")),
				Component.literal("a label isn't moved with a banner"));
		helper.assertTrue(MarkManager.edit(player, menu, level, new MarkEditPayload(5, mapId.id(), MarkEditPayload.Action.MOVE, pin.id(), 10, 12, 0, "")),
				Component.literal("pin moved"));
		helper.assertValueEqual(data.find(mapId).findMark(pin.id()).y(), 12, Component.literal("pin's new spot"));
		helper.assertTrue(MarkManager.edit(player, menu, level, new MarkEditPayload(6, mapId.id(), MarkEditPayload.Action.REMOVE, pin.id(), 0, 0, 0, "")),
				Component.literal("pin removed"));
		helper.assertValueEqual(data.find(mapId).marks().size(), 1, Component.literal("only the label left"));

		data.seal(mapId, player.getUUID());
		menu.setCarried(new ItemStack(Items.OAK_SIGN));
		helper.assertFalse(MarkManager.edit(player, menu, level, new MarkEditPayload(7, mapId.id(), MarkEditPayload.Action.WRITE, -1, 5, 5, 0, "Nope")),
				Component.literal("no labels on a sealed map"));
		helper.succeed();
	}

	/** A seal belongs to whoever sealed the map and to the map's maker; anyone else's axe can't scrape it off. */
	@GameTest
	public void sealsBelongToTheirOwners(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
		Player sealer = helper.makeMockPlayer(GameType.SURVIVAL);
		Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		InkData data = InkData.get(level.getServer());
		data.setMaker(mapId, maker.getUUID());
		data.seal(mapId, sealer.getUUID());

		helper.assertTrue(data.mayScrape(sealer, mapId), Component.literal("the sealer may scrape it"));
		helper.assertTrue(data.mayScrape(maker, mapId), Component.literal("the maker may scrape it"));
		helper.assertFalse(data.mayScrape(stranger, mapId), Component.literal("a stranger may not"));

		CartographyTableMenu menu = desk(helper, stranger);
		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(mapId));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.IRON_AXE));
		helper.assertTrue(menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem().isEmpty(), Component.literal("no result for a stranger's axe"));
		helper.succeed();
	}

	/** Washing waters labels down until they're gone; pins are banners, not ink, and stay. */
	@GameTest
	public void washingKeepsPins(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		InkLayer layer = InkData.get(level.getServer()).getOrCreate(mapId);
		layer.putMark(new Mark(1, Mark.Kind.LABEL, 10, 10, DyeColor.BLACK, "Farm", Mark.MEDIUM, Optional.empty()));
		layer.putMark(new Mark(2, Mark.Kind.PIN, 20, 20, DyeColor.RED, "", Mark.MEDIUM, Optional.empty()));
		helper.assertTrue(InkManager.wash(level.getServer(), mapId), Component.literal("a label washes"));
		helper.assertValueEqual(layer.findMark(1).fade(), 1, Component.literal("the label fades a step"));
		for (int i = 0; i < 3; i++) {
			InkManager.wash(level.getServer(), mapId);
		}
		helper.assertTrue(layer.marks().size() == 1 && !layer.marks().getFirst().isLabel(), Component.literal("the label washed off, the pin stays"));
		helper.succeed();
	}

	/**
	 * Watered down ink costs less, tints the map, and only water makes it
	 * fainter. Players without the mod see it as a pattern over the map.
	 */
	@GameTest
	public void tintsAreWateredDownInk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = desk(helper, player);
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		MapItemSavedData map = level.getMapData(mapId);
		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(mapId));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DYE.pick(DyeColor.RED), 1));

		InkManager.draw(player, menu, level, new InkStrokePayload(1, mapId.id(), false, 2, pixels(0, 1, 128, 129)));
		InkLayer layer = InkData.get(level.getServer()).find(mapId);
		helper.assertValueEqual(Ink.strength(layer.get(0)), 2, Component.literal("drawn at half strength"));
		InkMaterial red = new InkMaterial(InkMaterial.Kind.DYE, DyeColor.RED);
		helper.assertValueEqual(red.charge(player.getAttachedOrElse(InkSupply.ATTACHMENT, InkSupply.EMPTY)), red.perItem() - 2,
				Component.literal("four pixels at half strength cost two"));

		MapItemSavedData.MapPatch patch = firstPatch(helper, map, mapId, helper.makeMockPlayer(GameType.SURVIVAL));
		helper.assertValueEqual(patch.mapColors()[(0 - patch.startX()) - patch.startY() * patch.width()], Ink.toMapColor(layer.get(0)),
				Component.literal("half strength shows on its half of the pattern"));
		helper.assertValueEqual(patch.mapColors()[(1 - patch.startX()) - patch.startY() * patch.width()], map.colors[1],
				Component.literal("and the map shows through the other half"));

		InkManager.draw(player, menu, level, new InkStrokePayload(2, mapId.id(), false, 3, pixels(0)));
		helper.assertValueEqual(Ink.strength(layer.get(0)), 2, Component.literal("fainter dye doesn't water ink down"));
		InkManager.draw(player, menu, level, new InkStrokePayload(3, mapId.id(), false, 1, pixels(0)));
		helper.assertValueEqual(Ink.strength(layer.get(0)), 1, Component.literal("stronger dye makes it stronger"));

		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(PotionContents.createItemStack(Items.POTION, Potions.WATER));
		InkManager.draw(player, menu, level, new InkStrokePayload(4, mapId.id(), false, 1, pixels(0, 1)));
		helper.assertValueEqual(Ink.strength(layer.get(0)), 2, Component.literal("watered water fades a step"));
		helper.assertValueEqual(Ink.strength(layer.get(1)), 3, Component.literal("to the faintest"));
		InkManager.draw(player, menu, level, new InkStrokePayload(5, mapId.id(), false, 1, pixels(1)));
		helper.assertFalse(Ink.isPresent(layer.get(1)), Component.literal("and past it, off the map"));
		helper.succeed();
	}

	/** A sherd stamps its picture in the second slot's dye; marks are sized by scrolling, and growing a label costs ink. */
	@GameTest
	public void iconsAndSizes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		CartographyTableMenu menu = desk(helper, player);
		helper.setBlock(new BlockPos(0, 0, 1), Blocks.LOOM);
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		InkData data = InkData.get(level.getServer());
		menu.getSlot(CartographyTableMenu.MAP_SLOT).set(mapItem(mapId));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DYE.pick(DyeColor.GREEN), 1));
		InkMaterial green = new InkMaterial(InkMaterial.Kind.DYE, DyeColor.GREEN);

		ItemStack sherd = new ItemStack(Items.SHEAF_POTTERY_SHERD);
		sherd.set(DataComponents.CUSTOM_NAME, Component.literal("Wheat farm"));
		menu.setCarried(sherd);
		helper.assertTrue(MarkManager.edit(player, menu, level, new MarkEditPayload(1, mapId.id(), MarkEditPayload.Action.ICON, -1, 50, 50, Mark.MEDIUM, "")),
				Component.literal("icon stamped"));
		Mark icon = data.find(mapId).marks().getFirst();
		helper.assertTrue(icon.kind() == Mark.Kind.ICON && icon.color() == DyeColor.GREEN && icon.symbol().contains("sheaf")
				&& icon.text().equals("Wheat farm"), Component.literal("a green sheaf named after its sherd, got " + icon));
		helper.assertValueEqual(green.charge(player.getAttachedOrElse(InkSupply.ATTACHMENT, InkSupply.EMPTY)), green.perItem() - Mark.iconCost(Mark.MEDIUM),
				Component.literal("the icon costs its ink"));

		menu.setCarried(new ItemStack(Items.OAK_SIGN));
		helper.assertTrue(MarkManager.edit(player, menu, level, new MarkEditPayload(2, mapId.id(), MarkEditPayload.Action.WRITE, -1, 20, 20, Mark.SMALL, "Farms")),
				Component.literal("label written"));
		Mark label = data.find(mapId).marks().get(1);
		int before = green.charge(player.getAttachedOrElse(InkSupply.ATTACHMENT, InkSupply.EMPTY));
		helper.assertTrue(MarkManager.edit(player, menu, level, new MarkEditPayload(3, mapId.id(), MarkEditPayload.Action.RESIZE, label.id(), 0, 0, Mark.LARGE, "")),
				Component.literal("label made larger"));
		helper.assertValueEqual(data.find(mapId).findMark(label.id()).size(), Mark.LARGE, Component.literal("label's new size"));
		helper.assertValueEqual(before - green.charge(player.getAttachedOrElse(InkSupply.ATTACHMENT, InkSupply.EMPTY)),
				Mark.cost("Farms", Mark.LARGE) - Mark.cost("Farms", Mark.SMALL), Component.literal("growing pays for the difference"));
		helper.assertFalse(MarkManager.edit(player, menu, level, new MarkEditPayload(4, mapId.id(), MarkEditPayload.Action.RESIZE, icon.id(), 0, 0, Mark.SMALL, "")),
				Component.literal("an icon isn't sized with a sign"));
		menu.setCarried(sherd);
		helper.assertTrue(MarkManager.edit(player, menu, level, new MarkEditPayload(5, mapId.id(), MarkEditPayload.Action.RESIZE, icon.id(), 0, 0, Mark.SMALL, "")),
				Component.literal("icon made smaller, with its sherd"));
		menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.GLOW_INK_SAC, 1));
		helper.assertTrue(MarkManager.edit(player, menu, level, new MarkEditPayload(6, mapId.id(), MarkEditPayload.Action.GLOW, icon.id(), 0, 0, 0, "")),
				Component.literal("icon made to glow"));
		helper.assertTrue(data.find(mapId).findMark(icon.id()).glow(), Component.literal("the icon glows"));
		helper.succeed();
	}

	/**
	 * A border post holds the land around it on maps, as far as it reaches;
	 * water stops it, and the map's border shows in the nation's color.
	 */
	@GameTest
	public void postsHoldLand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		MapItemSavedData map = level.getMapData(mapId);
		byte grass = MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL);
		java.util.Arrays.fill(map.colors, grass);
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		int px = MapPixels.pixelX(map, at.getX());
		int pz = MapPixels.pixelZ(map, at.getZ());
		// A river two pixels east of the post, running the whole height of the map.
		for (int z = 0; z < Ink.SIZE; z++) {
			map.colors[Math.min(Ink.SIZE - 1, px + 2) + z * Ink.SIZE] = MapColor.WATER.getPackedId(MapColor.Brightness.NORMAL);
		}
		List<Post> posts = List.of(new Post(level.dimension(), at, DyeColor.RED, List.of(), "Redland"));
		Territory territory = Territory.of(map, posts, 12);
		helper.assertValueEqual(territory.owner(px + pz * Ink.SIZE), 1, Component.literal("the post's own spot"));
		helper.assertValueEqual(territory.owner(Math.max(0, px - 8) + pz * Ink.SIZE), 1, Component.literal("land within reach"));
		helper.assertValueEqual(territory.owner(Math.max(0, px - 20) + pz * Ink.SIZE), 0, Component.literal("land out of reach"));
		helper.assertValueEqual(territory.owner(Math.min(Ink.SIZE - 1, px + 4) + pz * Ink.SIZE), 0, Component.literal("land across the river"));
		boolean border = false;
		for (int i = 0; i < Ink.PIXELS; i++) {
			border |= territory.isBorder(i);
		}
		helper.assertTrue(border, Component.literal("the land has a border"));
		helper.succeed();
	}

	/** A cave map shows rock as its darkest shade and open floor as it is. */
	@GameTest
	public void caveMapsShowTheirLevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		MapItemSavedData map = level.getMapData(mapId);
		BlockPos wall = helper.absolutePos(new BlockPos(1, 2, 1));
		BlockPos open = helper.absolutePos(new BlockPos(3, 2, 3));
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.COBBLESTONE);
		MapPixels.ChunkLookup chunks = MapPixels.ChunkLookup.loaded(level);
		int depth = wall.getY();
		helper.assertValueEqual(MapPixels.caveColor(level, chunks, map, MapPixels.pixelX(map, wall.getX()), MapPixels.pixelZ(map, wall.getZ()), depth),
				MapColor.STONE.getPackedId(MapColor.Brightness.LOWEST) & 0xFF, Component.literal("rock"));
		helper.assertValueEqual(MapPixels.caveColor(level, chunks, map, MapPixels.pixelX(map, open.getX()), MapPixels.pixelZ(map, open.getZ()), depth),
				MapColor.STONE.getPackedId(MapColor.Brightness.HIGH) & 0xFF, Component.literal("the floor just below"));

		InkData.get(level.getServer()).setDepth(mapId, depth);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		// A cave map draws at its holder's head height.
		player.setPos(open.getX() + 0.5, depth - 1, open.getZ() + 0.5);
		ItemStack mapStack = mapItem(mapId);
		CaveMaps.ring(level, player, mapStack, false);
		helper.assertValueEqual(map.colors[MapPixels.pixelX(map, wall.getX()) + MapPixels.pixelZ(map, wall.getZ()) * Ink.SIZE],
				MapColor.STONE.getPackedId(MapColor.Brightness.LOWEST), Component.literal("an amethyst shard's ring maps the cave around"));
		helper.succeed();
	}

	/** A framed map is painted where it's used, with a pen in one hand and ink in the other, instead of turning. */
	@GameTest
	public void framedMapsArePainted(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(new BlockPos(1, 2, 1)), Direction.NORTH);
		frame.setItem(mapItem(mapId), false);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FEATHER));
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DYE.pick(DyeColor.BLUE), 4));
		helper.assertTrue(FramePainting.paints(player, frame, false), Component.literal("a feather and dye paint a framed map"));

		// Seen from in front of a frame facing north, its right is the west; a quarter of a block right and up.
		FramePainting.paint(level, player, frame, new Vec3(-0.25, 0.25, 0.0));
		InkLayer layer = InkData.get(level.getServer()).find(mapId);
		helper.assertTrue(layer != null && Ink.isPresent(layer.get(96 + 32 * Ink.SIZE)), Component.literal("painted where it was used"));
		helper.assertValueEqual(frame.getRotation(), 0, Component.literal("the map didn't turn"));

		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.assertFalse(FramePainting.paints(player, frame, false), Component.literal("ink alone doesn't paint"));
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

	/** A walked trail claims itself and the land it closes in, but never another nation's land. */
	@GameTest
	public void walkedBoundsClaimWhatTheyCloseIn(GameTestHelper helper) {
		// A loop 40 blocks across, back to where it began, around a small patch of another nation's land.
		int[] loop = {0, 0, 40, 0, 40, 40, 0, 40, 0, 0};
		Claim claim = Claim.annex(loop, (x, z) -> false, (x, z) -> x >= 30 && x < 34 && z >= 30 && z < 34);
		helper.assertTrue(claim.holds(20, 20), Component.literal("the land inside the loop"));
		helper.assertTrue(claim.holds(40, 20), Component.literal("the trail itself"));
		helper.assertFalse(claim.holds(60, 20), Component.literal("the land outside"));
		helper.assertFalse(claim.holds(31, 31), Component.literal("another nation's land inside the loop"));
		Claim decoded = Claim.CODEC.parse(NbtOps.INSTANCE, Claim.CODEC.encodeStart(NbtOps.INSTANCE, claim).getOrThrow()).getOrThrow();
		helper.assertValueEqual(decoded.area(), claim.area(), Component.literal("a claim saved and read back"));

		// A trail that loops nowhere takes only itself.
		Claim line = Claim.annex(new int[] {0, 0, 40, 0}, (x, z) -> false, (x, z) -> false);
		helper.assertTrue(line.holds(20, 0), Component.literal("the trail"));
		helper.assertFalse(line.holds(20, 20), Component.literal("nothing beside it"));

		// Out of the nation's own land and back into it closes in the land between.
		Claim back = Claim.annex(new int[] {0, 0, 0, 40, 40, 40, 40, 0}, (x, z) -> z < 2, (x, z) -> false);
		helper.assertTrue(back.holds(20, 20), Component.literal("land closed in by the trail and the nation's own land"));
		helper.succeed();
	}

	/** Wearing a named banner walks the bounds of land; planted, it claims them, and the map in hand shows the trail. */
	@GameTest
	public void marchingBannersClaimLand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack banner = new ItemStack(Items.BANNER.pick(DyeColor.LIME));
		helper.assertValueEqual(banner.get(DataComponents.EQUIPPABLE) == null ? null : banner.get(DataComponents.EQUIPPABLE).slot(),
				EquipmentSlot.HEAD, Component.literal("a banner is worn on the head"));
		banner.set(DataComponents.CUSTOM_NAME, Component.literal("Limeland"));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemSlot(EquipmentSlot.HEAD, banner);
		BlockPos start = helper.absolutePos(new BlockPos(1, 1, 1));
		int x = start.getX();
		int z = start.getZ();
		MapId mapId = newMap(level, start);
		MapItemSavedData map = level.getMapData(mapId);
		map.getHoldingPlayer(player);
		map.getUpdatePacket(mapId, player);

		int[][] walk = {{0, 0}, {24, 0}, {24, 24}, {0, 24}, {0, 0}};
		for (int[] step : walk) {
			player.setPos(x + step[0] + 0.5, start.getY(), z + step[1] + 0.5);
			Marches.step(player);
		}
		Bounds bounds = Bounds.read(player.getItemBySlot(EquipmentSlot.HEAD));
		helper.assertTrue(bounds != null && bounds.closed(), Component.literal("back at the start, the bounds are walked"));

		// The map held shows the trail, dashed, in the banner's color.
		byte lime = DyeColor.LIME.getMapColor().getPackedId(MapColor.Brightness.NORMAL);
		MapItemSavedData.MapPatch patch = firstPatch(helper, map, mapId, player);
		boolean trail = false;
		for (int i = 0; i < patch.width(); i++) {
			trail |= patch.mapColors()[i + (MapPixels.pixelZ(map, z) - patch.startY()) * patch.width()] == lime;
		}
		helper.assertTrue(trail, Component.literal("the trail shows on the map in hand"));

		BlockPos post = helper.absolutePos(new BlockPos(5, 1, 5));
		AbstractBannerBlock limeBanner = (AbstractBannerBlock) ((BlockItem) Items.BANNER.pick(DyeColor.LIME)).getBlock();
		helper.setBlock(new BlockPos(5, 1, 5), limeBanner);
		Nations.placed(level, post, limeBanner, player, player.getItemBySlot(EquipmentSlot.HEAD));
		Post planted = NationData.get(level.getServer()).posts().stream().filter(p -> p.pos().equals(post)).findFirst()
				.orElseThrow(() -> helper.assertionException(Component.literal("the banner should be a post")));
		helper.assertTrue(planted.claim().holds(x + 12, z + 12), Component.literal("the land inside the walked bounds"));
		helper.assertFalse(planted.claim().holds(x + 40, z + 12), Component.literal("the land outside them"));
		NationData.get(level.getServer()).remove(level.dimension(), post);
		helper.succeed();
	}

	/** A map shows a nation's land only once the nation's banner is marked on it. */
	@GameTest
	public void bordersShowOnceTheBannerIsOnTheMap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MapId mapId = newMap(level, helper.absolutePos(BlockPos.ZERO));
		MapItemSavedData map = level.getMapData(mapId);
		java.util.Arrays.fill(map.colors, MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL));
		BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.setBlock(new BlockPos(2, 1, 2), ((BlockItem) Items.BANNER.pick(DyeColor.ORANGE)).getBlock());
		NationData data = NationData.get(level.getServer());
		data.put(new Post(level.dimension(), at, DyeColor.ORANGE, List.of(), "Orangeland"));
		helper.assertTrue(Nations.territory(level.getServer(), mapId, map).isEmpty(), Component.literal("no borders before the banner is marked"));
		helper.assertTrue(map.toggleBanner(level, at), Component.literal("using the map on the banner marks it"));
		Territory shown = Nations.territory(level.getServer(), mapId, map);
		helper.assertValueEqual(shown.owner(MapPixels.pixelX(map, at.getX()) + MapPixels.pixelZ(map, at.getZ()) * Ink.SIZE), 1,
				Component.literal("the nation's land, once its banner is on the map"));
		data.remove(level.dimension(), at);
		helper.succeed();
	}

	/** A book and quill's empty pages take sketches, paid for with the ink in the other hand; pages with text don't. */
	@GameTest
	public void sketchesAreDrawnInBooks(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
		book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(Filterable.passThrough("Notes"))));
		player.setItemInHand(InteractionHand.MAIN_HAND, book);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DYE.pick(DyeColor.GREEN), 2));
		BitSet line = new BitSet(Sketch.PIXELS);
		for (int x = 10; x < 60; x++) {
			line.set(x + 20 * Sketch.WIDTH);
		}
		Sketching.draw(player, new SketchPayload(false, 1, Ink.FULL, false, line));
		Atlas atlas = Atlas.read(player.getMainHandItem()).orElseThrow(() -> helper.assertionException(Component.literal("the book has a sketch")));
		Sketch sketch = atlas.sketchAt(1);
		helper.assertTrue(sketch != null && Ink.isPresent(sketch.get(30 + 20 * Sketch.WIDTH)) && Ink.dye(sketch.get(30 + 20 * Sketch.WIDTH)) == DyeColor.GREEN,
				Component.literal("a green line on the empty page"));
		helper.assertValueEqual(atlas.requiredPages(), 2, Component.literal("the sketched page is kept"));

		Sketching.draw(player, new SketchPayload(false, 0, Ink.FULL, false, line));
		helper.assertTrue(Atlas.read(player.getMainHandItem()).orElseThrow().sketchAt(0) == null, Component.literal("no sketch over text"));

		Sketching.draw(player, new SketchPayload(false, 1, Ink.FULL, true, line));
		helper.assertTrue(Atlas.read(player.getMainHandItem()).orElseThrow().sketchAt(1) == null, Component.literal("erased, the page is empty again"));
		helper.succeed();
	}
}
