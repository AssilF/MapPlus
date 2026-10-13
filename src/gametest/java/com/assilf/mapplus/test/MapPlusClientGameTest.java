package com.assilf.mapplus.test;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.client.mixin.AbstractContainerScreenAccessor;
import com.assilf.mapplus.ink.InkData;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;
import java.util.function.Consumer;

/**
 * Plays through the drawing desk and an atlas in a real client, taking
 * screenshots on the way.
 */
public class MapPlusClientGameTest implements FabricClientGameTest {
	private static final int CANVAS_X = 179;
	private static final int CANVAS_Y = 8;
	private static final int TOOL_X = 178;
	private static final int TOOL_Y = 141;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("weather clear");

			ItemStack explored = server.computeOnServer(MapPlusClientGameTest::exploredMap);

			// The drawing desk with an explored map and red dye.
			server.runOnServer(minecraft -> openTable(minecraft, menu -> {
				menu.getSlot(CartographyTableMenu.MAP_SLOT).set(explored.copy());
				menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DYE.pick(DyeColor.RED), 3));
			}));
			context.waitForScreen(CartographyTableScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("desk_map");

			// A pen stroke, then a box with a thicker brush.
			penStroke(context, 20, 20, 100, 60);
			clickTool(context, 2);
			scrollCanvas(context, 64, 64, 1);
			drag(context, 30, 80, 90, 115);
			context.waitTicks(10);
			context.takeScreenshot("desk_drawn");

			hover(context, TOOL_X + 3 * 16 + 8, TOOL_Y + 9);
			context.waitTicks(2);
			context.takeScreenshot("desk_tool_tooltip");

			// An empty map is a blank canvas; the drawing is the result.
			server.runOnServer(minecraft -> withTable(minecraft, menu -> {
				menu.getSlot(CartographyTableMenu.MAP_SLOT).set(new ItemStack(Items.MAP, 2));
				menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.INK_SAC, 2));
			}));
			context.waitTicks(5);
			clickTool(context, 0);
			penStroke(context, 10, 64, 118, 64);
			penStroke(context, 64, 10, 64, 118);
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
		}
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

	private static void clickTool(ClientGameTestContext context, int index) {
		hover(context, TOOL_X + index * 16 + 8, TOOL_Y + 9);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	private static void scrollCanvas(ClientGameTestContext context, int x, int y, double amount) {
		hover(context, CANVAS_X + x, CANVAS_Y + y);
		context.getInput().scroll(amount);
		context.waitTick();
	}

	private static void penStroke(ClientGameTestContext context, int x0, int y0, int x1, int y1) {
		drag(context, x0, y0, x1, y1);
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
