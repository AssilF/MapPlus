package com.assilf.mapplus.test;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.atlas.Sketch;
import com.assilf.mapplus.client.atlas.LecternView;
import com.assilf.mapplus.client.atlas.SketchBookScreen;
import com.assilf.mapplus.client.foresight.Projection;
import com.assilf.mapplus.client.scope.FarLand;
import com.assilf.mapplus.client.mixin.AbstractContainerScreenAccessor;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.InkData;
import com.assilf.mapplus.ink.InkLayer;
import com.assilf.mapplus.ink.Mark;
import com.assilf.mapplus.nation.NationData;
import com.assilf.mapplus.nation.Post;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import com.assilf.mapplus.survey.MapPixels;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;
import java.util.function.Consumer;

/**
 * Plays through the drawing desk and an atlas in a real client, taking
 * screenshots on the way.
 */
public class MapPlusClientGameTest implements FabricClientGameTest {
	private static final int CANVAS_X = 187;
	private static final int CANVAS_Y = 8;
	private static final int RACK_X = 179;
	private static final int RACK_Y = 141;
	private static final int FEATHER = 0;
	private static final int BOX = 3;
	private static final int BUCKET = 4;
	private static final int CHAIN = 5;
	private static final int SIGN = 6;
	private static final int BANNER = 7;
	private static final int SHERD = 8;
	private static final int ZOOM_X = 323;
	/** Far from the world's middle, where a map's center matters. */
	private static final int FAR = 1000;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("weather clear");

			ItemStack explored = server.computeOnServer(MapPlusClientGameTest::exploredMap);
			server.runOnServer(minecraft -> {
				ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
				ItemStack banner = new ItemStack(Items.BANNER.pick(DyeColor.BLUE));
				banner.set(DataComponents.CUSTOM_NAME, Component.literal("Town Hall"));
				banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(List.of(new BannerPatternLayers.Layer(
						minecraft.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(BannerPatterns.CREEPER), DyeColor.WHITE))));
				ItemStack sherd = new ItemStack(Items.SKULL_POTTERY_SHERD);
				sherd.set(DataComponents.CUSTOM_NAME, Component.literal("Danger"));
				for (ItemStack tool : List.of(new ItemStack(Items.FEATHER), new ItemStack(Items.ITEM_FRAME), new ItemStack(Items.BUCKET),
						new ItemStack(Items.IRON_CHAIN), new ItemStack(Items.OAK_SIGN), banner, sherd)) {
					player.getInventory().add(tool);
				}
			});

			// The drawing desk with an explored map and red dye, and a loom next to it.
			server.runOnServer(minecraft -> openTable(minecraft, menu -> {
				menu.getSlot(CartographyTableMenu.MAP_SLOT).set(explored.copy());
				menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DYE.pick(DyeColor.RED), 32));
			}));
			context.waitForScreen(CartographyTableScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("desk_map");

			// A feather stroke, then a box drawn with an item frame.
			clickTool(context, FEATHER);
			drag(context, 20, 20, 100, 60);
			clickTool(context, BOX);
			drag(context, 30, 80, 90, 115);
			context.waitTicks(10);
			context.takeScreenshot("desk_drawn");

			hover(context, RACK_X + CHAIN * 16 + 8, RACK_Y + 9);
			context.waitTicks(2);
			context.takeScreenshot("desk_tool_tooltip");

			// Marking up: a plot fenced with a dashed border and tinted with watered down ink, the box filled, a large label
			// typed with a sign, a banner pinned and a sherd's picture stamped.
			clickTool(context, CHAIN);
			drag(context, 4, 70, 26, 70);
			drag(context, 26, 70, 26, 122);
			drag(context, 26, 122, 4, 122);
			drag(context, 4, 122, 4, 70);
			clickTool(context, BUCKET);
			context.waitTicks(5);
			hover(context, CANVAS_X + 15, CANVAS_Y + 95);
			context.getInput().scroll(-2);
			context.waitTicks(2);
			context.takeScreenshot("desk_strength");
			clickCanvas(context, 15, 95);
			context.getInput().scroll(2);
			clickCanvas(context, 60, 100);
			clickTool(context, SIGN);
			clickCanvas(context, 60, 30);
			context.getInput().typeChars("Base Camp");
			context.getInput().pressKey(InputConstants.KEY_UP);
			context.waitTicks(2);
			context.takeScreenshot("desk_label_typing");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			clickTool(context, BANNER);
			clickCanvas(context, 100, 98);
			clickTool(context, SHERD);
			clickCanvas(context, 100, 40);
			context.waitTicks(20);
			context.takeScreenshot("desk_marks");
			MapId exploredId = explored.get(DataComponents.MAP_ID);
			List<Mark> marks = server.computeOnServer(minecraft -> {
				InkLayer layer = InkData.get(minecraft).find(exploredId);
				return layer == null ? List.of() : List.copyOf(layer.marks());
			});
			if (marks.size() != 3) {
				throw new AssertionError("Expected a label, a pin and an icon on the map, found " + marks);
			}
			if (marks.get(0).size() != Mark.LARGE || marks.get(1).layers().size() != 1 || !marks.get(2).symbol().contains("skull")) {
				throw new AssertionError("Expected a large label, a pin with the banner's pattern and the skull sherd's icon, found " + marks);
			}
			// The fill stays inside the dashed border: nothing leaks out through the gaps between its dashes.
			boolean[] fill = server.computeOnServer(minecraft -> {
				InkLayer layer = InkData.get(minecraft).find(exploredId);
				return new boolean[] {
						layer != null && Ink.isPresent(layer.get(15 + 95 * Ink.SIZE)) && Ink.strength(layer.get(15 + 95 * Ink.SIZE)) == 2,
						layer != null && Ink.isPresent(layer.get(1 + 95 * Ink.SIZE)),
						layer != null && Ink.isPresent(layer.get(15 + 126 * Ink.SIZE))
				};
			});
			if (!fill[0] || fill[1] || fill[2]) {
				throw new AssertionError("The fill should tint the fenced plot at half strength and nothing outside it");
			}

			// The marked-up map held in both hands, drawn in the world: the tint, the label, the banner and the icon, and a
			// nation whose border post stands to the east, marked on the map.
			server.runOnServer(minecraft -> {
				ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
				player.closeContainer();
				player.setItemInHand(InteractionHand.MAIN_HAND, explored.copy());
				BlockPos post = player.blockPosition().east(24);
				player.level().setBlockAndUpdate(post, ((BlockItem) Items.BANNER.pick(DyeColor.BLUE)).getBlock().defaultBlockState());
				NationData.get(minecraft).put(new Post(player.level().dimension(), post, DyeColor.BLUE, List.of(), "Blueland"));
				player.level().getMapData(explored.get(DataComponents.MAP_ID)).toggleBanner(player.level(), post);
			});
			context.waitTicks(25);
			context.getInput().lookAt(0, 70);
			context.waitTicks(20);
			context.takeScreenshot("held_map");

			// Foresight: with the map in the off hand, its ink and labels are projected onto the land around: looking north,
			// the feather's line and the label written across the ground.
			server.runOnServer(minecraft -> {
				ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
				ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
				helmet.enchant(minecraft.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Projection.FORESIGHT), 1);
				player.setItemSlot(EquipmentSlot.HEAD, helmet);
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				player.setItemInHand(InteractionHand.OFF_HAND, explored.copy());
			});
			context.waitTicks(10);
			context.getInput().lookAt(180, 30);
			context.waitTicks(10);
			context.takeScreenshot("foresight");

			// Looking through a spyglass with the map in the other hand shows the map in the corner; scrolling zooms.
			server.runOnServer(minecraft -> {
				ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS));
			});
			context.waitTicks(5);
			context.getInput().lookAt(90, 10);
			context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(20);
			context.takeScreenshot("scope_map");
			context.getInput().scroll(3);
			context.waitTicks(10);
			context.takeScreenshot("scope_zoomed");
			// Far view: looking out to the horizon, the land past the render distance is sent and drawn.
			context.getInput().scroll(-3);
			context.getInput().lookAt(90, 0);
			context.waitTicks(60);
			context.takeScreenshot("far_view");
			int farPatches = context.computeOnClient(client -> FarLand.kept());
			if (farPatches == 0) {
				throw new AssertionError("No far land was sent while looking through the spyglass");
			}
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(5);

			server.runOnServer(minecraft -> openTable(minecraft, menu -> {
			}));
			context.waitForScreen(CartographyTableScreen.class);

			// The drafting table: the explored map on the table and the map of the land to its east carried, laid out
			// together, and a border drawn with the chain from one straight across into the other.
			ItemStack east = server.computeOnServer(minecraft -> exploredMap(minecraft, Ink.SIZE));
			server.runOnServer(minecraft -> {
				minecraft.getPlayerList().getPlayers().getFirst().getInventory().add(east.copy());
				withTable(minecraft, menu -> {
					menu.getSlot(CartographyTableMenu.MAP_SLOT).set(explored.copy());
					menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DYE.pick(DyeColor.RED), 32));
				});
			});
			context.waitTicks(10);
			clickTool(context, CHAIN);
			hover(context, ZOOM_X + 5, RACK_Y + 9);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(5);
			drag(context, 50, 60, 120, 75);
			context.waitTicks(10);
			context.takeScreenshot("desk_sheet");
			int eastId = east.get(DataComponents.MAP_ID).id();
			boolean crossed = server.computeOnServer(minecraft -> {
				InkLayer homeInk = InkData.get(minecraft).find(explored.get(DataComponents.MAP_ID));
				InkLayer eastInk = InkData.get(minecraft).find(new MapId(eastId));
				return homeInk != null && !homeInk.isEmpty() && eastInk != null && !eastInk.isEmpty();
			});
			if (!crossed) {
				throw new AssertionError("A border drawn across the sheet should ink both the table's map and the one beside it");
			}
			// With an empty hand, clicking the map to the east puts it on the table, and the table's map in its place.
			clickTool(context, CHAIN);
			hover(context, CANVAS_X + 106, CANVAS_Y + 64);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(10);
			context.takeScreenshot("desk_sheet_swapped");
			boolean swapped = server.computeOnServer(minecraft -> {
				ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
				ItemStack onTable = player.containerMenu instanceof CartographyTableMenu menu
						? menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem() : ItemStack.EMPTY;
				return eastId == (onTable.has(DataComponents.MAP_ID) ? onTable.get(DataComponents.MAP_ID).id() : -1)
						&& player.getInventory().contains(stack -> explored.get(DataComponents.MAP_ID).equals(stack.get(DataComponents.MAP_ID)));
			});
			if (!swapped) {
				throw new AssertionError("Clicking the carried map with an empty hand should put it on the table");
			}
			hover(context, ZOOM_X + 5, RACK_Y + 9);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(3);

			// Paper is a blank canvas; the drawing is the result.
			server.runOnServer(minecraft -> withTable(minecraft, menu -> {
				menu.getSlot(CartographyTableMenu.MAP_SLOT).set(new ItemStack(Items.PAPER, 2));
				menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.INK_SAC, 2));
			}));
			context.waitTicks(5);
			clickTool(context, FEATHER);
			drag(context, 10, 64, 118, 64);
			drag(context, 64, 10, 64, 118);
			context.waitTicks(10);
			context.takeScreenshot("desk_canvas");
			boolean canvasResult = context.computeOnClient(client -> client.gui.screen() instanceof CartographyTableScreen table
					&& table.getMenu().getSlot(CartographyTableMenu.RESULT_SLOT).getItem().is(Items.FILLED_MAP));
			if (!canvasResult) {
				throw new AssertionError("The drawing on the empty map should be in the result slot");
			}

			hover(context, CANVAS_X + 64, CANVAS_Y + 64);
			server.runOnServer(minecraft -> withTable(minecraft, menu ->
					menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(ItemStack.EMPTY)));
			context.waitTicks(3);
			context.takeScreenshot("desk_hint");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(2);
			// Closing the table didn't lose the drawing: it's in the inventory.
			boolean keptDrawing = server.computeOnServer(minecraft -> {
				ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
				InkData ink = InkData.get(minecraft);
				return player.getInventory().contains(stack -> {
					MapId mapId = stack.get(DataComponents.MAP_ID);
					return stack.is(Items.FILLED_MAP) && mapId != null && ink.find(mapId) != null && !ink.find(mapId).isEmpty();
				});
			});
			if (!keptDrawing) {
				throw new AssertionError("Closing the table should give back the drawing on the empty map");
			}

			// An atlas: notes on the left facing a map, then a map facing new notes.
			server.runOnServer(minecraft -> {
				ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
				ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
				book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(
						Filterable.passThrough("Base camp\n\nThe red line is the new road. The box is the farm."))));
				ItemStack atlas = Atlas.stitch(book, explored, minecraft.registryAccess());
				ItemStack second = exploredMap(minecraft);
				atlas = Atlas.stitch(atlas, second, minecraft.registryAccess());
				player.setItemInHand(InteractionHand.MAIN_HAND, atlas);
			});
			context.waitTicks(10);
			// Look at the sky, so using the atlas doesn't open the table instead.
			context.getInput().lookAt(0, -90);
			context.waitTick();
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(BookEditScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("atlas_notes_and_map");
			context.getInput().pressKey(InputConstants.KEY_PAGEDOWN);
			context.waitTicks(3);
			context.takeScreenshot("atlas_map_and_notes");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(2);

			// Signed, it reads the same way.
			server.runOnServer(minecraft -> {
				ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
				ItemStack atlas = player.getMainHandItem();
				WritableBookContent pages = atlas.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
				ItemStack signed = atlas.transmuteCopy(Items.WRITTEN_BOOK);
				signed.remove(DataComponents.WRITABLE_BOOK_CONTENT);
				signed.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Survey"), "Tester", 0,
						pages.pages().stream().map(page -> page.<Component>map(Component::literal)).toList(), true));
				player.setItemInHand(InteractionHand.MAIN_HAND, signed);
			});
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(BookViewScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("atlas_signed");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(2);

			// A sketchbook: a book and quill with green dye in the other hand; the quill draws on the empty second page.
			server.runOnServer(minecraft -> {
				ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
				ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
				book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(
						Filterable.passThrough("The watchtower\n\nSketched from the hill, facing north."))));
				player.setItemInHand(InteractionHand.MAIN_HAND, book);
				player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DYE.pick(DyeColor.GREEN), 16));
			});
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(BookEditScreen.class);
			context.waitTicks(3);
			context.getInput().pressKey(InputConstants.KEY_PAGEDOWN);
			context.waitTicks(3);
			// A tower: its walls, its roof, and a door.
			sketch(context, 40, 110, 40, 40);
			sketch(context, 40, 40, 74, 40);
			sketch(context, 74, 40, 74, 110);
			sketch(context, 34, 40, 57, 12);
			sketch(context, 57, 12, 80, 40);
			sketch(context, 52, 110, 52, 90);
			sketch(context, 52, 90, 62, 90);
			sketch(context, 62, 90, 62, 110);
			context.waitTicks(10);
			context.takeScreenshot("sketch_drawn");
			// On the written first page, the title underlined: a sketch goes under the writing too.
			context.getInput().pressKey(InputConstants.KEY_PAGEUP);
			context.waitTicks(3);
			sketch(context, 0, 9, 74, 9);
			sketch(context, 0, 11, 74, 11);
			context.waitTicks(10);
			context.takeScreenshot("sketch_under_text");
			clickDone(context);
			context.waitTicks(10);
			int[] kept = server.computeOnServer(minecraft -> {
				ItemStack book = minecraft.getPlayerList().getPlayers().getFirst().getMainHandItem();
				Sketch sketch = Atlas.read(book).map(atlas -> atlas.sketchAt(1)).orElse(null);
				int pages = book.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY).pages().size();
				return new int[] {sketch == null ? 0 : 1, pages};
			});
			if (kept[0] != 1 || kept[1] != 2) {
				throw new AssertionError("The sketch should be kept on the book's second page, found sketch " + kept[0] + " with " + kept[1] + " pages");
			}
			// Opened again, the sketch faces its notes as it was drawn.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(BookEditScreen.class);
			context.waitTicks(3);
			context.getInput().pressKey(InputConstants.KEY_PAGEDOWN);
			context.waitTicks(3);
			context.takeScreenshot("sketch_reopened");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(2);

			// The book in hand, the other hand free: held open as a map is, at the spread it was closed on.
			server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
			context.getInput().lookAt(180, 20);
			context.waitTicks(15);
			context.takeScreenshot("held_book");

			// On a lectern in front of the player, the book lies open at its page for players with the mod.
			server.runOnServer(minecraft -> {
				ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos pos = player.blockPosition().north(2);
				level.setBlockAndUpdate(pos, Blocks.LECTERN.defaultBlockState()
						.setValue(net.minecraft.world.level.block.LecternBlock.FACING, net.minecraft.core.Direction.SOUTH)
						.setValue(net.minecraft.world.level.block.LecternBlock.HAS_BOOK, true));
				if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity lectern) {
					lectern.setBook(player.getMainHandItem().copy());
				}
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			});
			context.getInput().lookAt(180, 40);
			context.waitTicks(30);
			context.takeScreenshot("lectern_book");
			boolean onLectern = context.computeOnClient(client -> LecternView.at(client.player.blockPosition().north(2)) != null);
			if (!onLectern) {
				throw new AssertionError("The lectern's book should have been sent to the player");
			}

			// Foresight far from the world's middle, where a map's own copy in the game can't tell where it lies: a cross
			// drawn on a map of the land there is projected around the player.
			int y = server.computeOnServer(minecraft -> {
				ServerLevel level = minecraft.overworld();
				level.getChunk(FAR >> 4, FAR >> 4);
				return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, FAR, FAR);
			});
			server.runCommand("tp @a " + FAR + ".5 " + y + " " + FAR + ".5 0 60");
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			server.runOnServer(minecraft -> {
				ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
				ItemStack far = exploredMap(minecraft);
				MapItemSavedData data = MapItem.getSavedData(far, player.level());
				InkLayer layer = InkData.get(minecraft).getOrCreate(far.get(DataComponents.MAP_ID));
				int px = MapPixels.pixelX(data, player.getBlockX());
				int pz = MapPixels.pixelZ(data, player.getBlockZ());
				for (int d = -12; d <= 12; d++) {
					layer.set(Math.clamp(px + d, 0, Ink.SIZE - 1) + pz * Ink.SIZE, Ink.encode(DyeColor.RED, false, Ink.FULL));
					layer.set(px + Math.clamp(pz + d, 0, Ink.SIZE - 1) * Ink.SIZE, Ink.encode(DyeColor.BLUE, false, Ink.FULL));
				}
				ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
				helmet.enchant(minecraft.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Projection.FORESIGHT), 1);
				player.setItemSlot(EquipmentSlot.HEAD, helmet);
				// In the off hand, so the map doesn't hide the ground.
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				player.setItemInHand(InteractionHand.OFF_HAND, far);
			});
			context.waitTicks(20);
			context.takeScreenshot("foresight_far");
			int squares = context.computeOnClient(client -> Projection.projectedSquares());
			if (squares < 20) {
				throw new AssertionError("Foresight should project the map's cross around the player far from the world's middle, found "
						+ squares + " squares");
			}
		}
	}

	/** Presses the open book's Done button, which saves it. */
	private static void clickDone(ClientGameTestContext context) {
		double[] at = context.computeOnClient(client -> {
			for (var child : client.gui.screen().children()) {
				if (child instanceof net.minecraft.client.gui.components.Button button
						&& button.getMessage().getString().equals(net.minecraft.network.chat.CommonComponents.GUI_DONE.getString())) {
					double scale = scale(client);
					return new double[] {(button.getX() + button.getWidth() / 2.0) * scale, (button.getY() + button.getHeight() / 2.0) * scale};
				}
			}
			throw new AssertionError("The book should have a Done button");
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	/** Draws a line with the quill on the open book's page, from one point of its writing area to another. */
	private static void sketch(ClientGameTestContext context, int x0, int y0, int x1, int y1) {
		int steps = 8;
		for (int i = 0; i <= steps; i++) {
			int x = x0 + (x1 - x0) * i / steps;
			int y = y0 + (y1 - y0) * i / steps;
			double[] at = context.computeOnClient(client -> {
				if (!(client.gui.screen() instanceof SketchBookScreen book)) {
					throw new AssertionError("The book and quill should be open");
				}
				double scale = scale(client);
				return new double[] {(book.mapplus$writingX() + x + 0.5) * scale, (book.mapplus$writingY() + y + 0.5) * scale};
			});
			context.getInput().setCursorPos(at[0], at[1]);
			context.waitTick();
			if (i == 0) {
				context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
				context.waitTick();
			}
		}
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	/** A map of the area around the player, filled in as if they had walked around with it. */
	private static ItemStack exploredMap(MinecraftServer minecraft) {
		return exploredMap(minecraft, 0);
	}

	/** A map of the land some blocks east of the player, filled in as far as they can see from where they stand. */
	private static ItemStack exploredMap(MinecraftServer minecraft, int east) {
		ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
		ServerLevel level = player.level();
		ItemStack map = MapItem.create(level, player.getBlockX() + east, player.getBlockZ(), (byte) 0, true, false);
		MapItemSavedData data = MapItem.getSavedData(map, level);
		for (int i = 0; i < 40; i++) {
			((MapItem) Items.FILLED_MAP).update(level, player, data);
		}
		return map;
	}

	private static void openTable(MinecraftServer minecraft, Consumer<CartographyTableMenu> setup) {
		ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition().east(2);
		level.setBlockAndUpdate(pos, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		level.setBlockAndUpdate(pos.north(), Blocks.LOOM.defaultBlockState());
		player.openMenu(level.getBlockState(pos).getMenuProvider(level, pos));
		withTable(minecraft, setup);
	}

	private static void withTable(MinecraftServer minecraft, Consumer<CartographyTableMenu> setup) {
		ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
		if (!(player.containerMenu instanceof CartographyTableMenu menu)) {
			throw new AssertionError("The cartography table should be open");
		}
		setup.accept(menu);
		menu.broadcastChanges();
	}

	/** Window coordinates of a point given relative to the table window. */
	private static double[] toWindow(ClientGameTestContext context, int x, int y) {
		return context.computeOnClient(client -> {
			if (!(client.gui.screen() instanceof CartographyTableScreen table)) {
				throw new AssertionError("The cartography table screen should be open");
			}
			AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) table;
			double scale = scale(client);
			return new double[] {(accessor.mapplus$leftPos() + x + 0.5) * scale, (accessor.mapplus$topPos() + y + 0.5) * scale};
		});
	}

	private static double scale(Minecraft client) {
		return client.getWindow().getScreenWidth() / (double) client.getWindow().getGuiScaledWidth();
	}

	private static void hover(ClientGameTestContext context, int x, int y) {
		double[] at = toWindow(context, x, y);
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTick();
	}

	/** Picks a tool up from the inventory with the rack under the map. */
	private static void clickTool(ClientGameTestContext context, int index) {
		hover(context, RACK_X + index * 16 + 8, RACK_Y + 9);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static void clickCanvas(ClientGameTestContext context, int x, int y) {
		hover(context, CANVAS_X + x, CANVAS_Y + y);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	/** Presses on the canvas at one pixel and releases at another, moving in steps. */
	private static void drag(ClientGameTestContext context, int x0, int y0, int x1, int y1) {
		hover(context, CANVAS_X + x0, CANVAS_Y + y0);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		int steps = 8;
		for (int i = 1; i <= steps; i++) {
			hover(context, CANVAS_X + x0 + (x1 - x0) * i / steps, CANVAS_Y + y0 + (y1 - y0) * i / steps);
		}
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}
}
