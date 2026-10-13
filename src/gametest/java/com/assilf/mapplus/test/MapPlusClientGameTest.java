package com.assilf.mapplus.test;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.atlas.Sketch;
import com.assilf.mapplus.client.atlas.SketchBookScreen;
import com.assilf.mapplus.client.foresight.Projection;
import com.assilf.mapplus.client.mixin.AbstractContainerScreenAccessor;
import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.InkData;
import com.assilf.mapplus.ink.InkLayer;
import com.assilf.mapplus.ink.Mark;
import com.assilf.mapplus.nation.Bounds;
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

			// The marked-up map held in both hands, drawn in the world: the tint, the label, the banner and the icon, a
			// nation whose border post stands to the east, marked on the map, and the dashed bounds the banner worn on the
			// player's head has walked.
			server.runOnServer(minecraft -> {
				ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
				player.closeContainer();
				player.setItemInHand(InteractionHand.MAIN_HAND, explored.copy());
				BlockPos post = player.blockPosition().east(24);
				player.level().setBlockAndUpdate(post, ((BlockItem) Items.BANNER.pick(DyeColor.BLUE)).getBlock().defaultBlockState());
				NationData.get(minecraft).put(new Post(player.level().dimension(), post, DyeColor.BLUE, List.of(), "Blueland"));
				player.level().getMapData(explored.get(DataComponents.MAP_ID)).toggleBanner(player.level(), post);
				ItemStack worn = new ItemStack(Items.BANNER.pick(DyeColor.LIME));
				worn.set(DataComponents.CUSTOM_NAME, Component.literal("Limeland"));
				BlockPos at = player.blockPosition();
				Bounds.start(player.level().dimension(), "Limeland", at.getX() - 30, at.getZ() - 10, false)
						.to(at.getX() - 6, at.getZ() - 30, 32).to(at.getX() + 10, at.getZ() - 4, 32).to(at.getX() - 10, at.getZ() + 20, 32)
						.closedAtStart().write(worn);
				player.setItemSlot(EquipmentSlot.HEAD, worn);
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
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(5);

			server.runOnServer(minecraft -> openTable(minecraft, menu -> {
			}));
			context.waitForScreen(CartographyTableScreen.class);

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
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
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
		}
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
		ServerPlayer player = minecraft.getPlayerList().getPlayers().getFirst();
		ServerLevel level = player.level();
		ItemStack map = MapItem.create(level, player.getBlockX(), player.getBlockZ(), (byte) 0, true, false);
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
