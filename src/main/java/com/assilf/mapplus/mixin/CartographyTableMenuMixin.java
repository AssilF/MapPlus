package com.assilf.mapplus.mixin;

import com.assilf.mapplus.MapPlus;
import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.ink.DeskSlot;
import com.assilf.mapplus.ink.DrawingDesk;
import com.assilf.mapplus.ink.InkData;
import com.assilf.mapplus.ink.InkManager;
import com.assilf.mapplus.ink.InkMaterial;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The cartography table as a drawing desk, for players who have the mod:
 * <ul>
 * <li>an empty map in the first slot is a blank canvas; the drawing on it is the result</li>
 * <li>a map plus a book and quill (or an atlas) stitches the map into the book</li>
 * <li>a map plus honeycomb seals its ink; a map plus an axe scrapes the seal off</li>
 * <li>shift-clicking dyes, ink sacs, water bottles, honeycomb, axes and books puts them in the second slot</li>
 * </ul>
 * Players without the mod get the vanilla table.
 */
@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuMixin extends AbstractContainerMenu implements DrawingDesk {
	@Shadow
	@Final
	private ContainerLevelAccess access;

	@Shadow
	@Final
	private ResultContainer resultContainer;

	@Shadow
	@Final
	public Container container;

	@Unique
	private Player mapplus$player;

	@Unique
	private boolean mapplus$desk;

	@Unique
	private MapId mapplus$canvas;

	protected CartographyTableMenuMixin(MenuType<?> type, int containerId) {
		super(type, containerId);
	}

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
	private void mapplus$init(int containerId, Inventory inventory, ContainerLevelAccess levelAccess, CallbackInfo ci) {
		mapplus$player = inventory.player;
		mapplus$desk = MapPlus.hasMod(inventory.player);
		((DeskSlot) slots.get(CartographyTableMenu.MAP_SLOT)).mapplus$setDesk(this);
		((DeskSlot) slots.get(CartographyTableMenu.ADDITIONAL_SLOT)).mapplus$setDesk(this);
		// A drawing left unfinished at another table carries on here.
		Integer pending = mapplus$desk ? inventory.player.getAttached(InkManager.PENDING_CANVAS) : null;
		if (pending != null && inventory.player.level().getMapData(new MapId(pending)) != null) {
			mapplus$canvas = new MapId(pending);
		}
	}

	@Override
	public boolean mapplus$isDesk() {
		return mapplus$desk;
	}

	@Override
	public void mapplus$setDesk(boolean desk) {
		mapplus$desk = desk;
	}

	@Override
	public MapId mapplus$canvas() {
		return mapplus$canvas;
	}

	@Override
	public void mapplus$setCanvas(MapId canvas) {
		mapplus$canvas = canvas;
		if (mapplus$player instanceof ServerPlayer) {
			if (canvas == null) {
				mapplus$player.removeAttached(InkManager.PENDING_CANVAS);
			} else {
				mapplus$player.setAttached(InkManager.PENDING_CANVAS, canvas.id());
			}
		}
		slotsChanged(container);
	}

	/**
	 * Closing the table with a drawing on an empty map gives back the drawing
	 * instead of that empty map, as if the result had been taken. Without an
	 * empty map left in the slot, the drawing waits for the player's next one.
	 */
	@Inject(method = "removed", at = @At("HEAD"))
	private void mapplus$keepDrawing(Player player, CallbackInfo ci) {
		if (!mapplus$desk || mapplus$canvas == null || !(player instanceof ServerPlayer serverPlayer)
				|| !container.getItem(CartographyTableMenu.MAP_SLOT).is(Items.MAP)) {
			return;
		}
		ItemStack drawing = DrawingDesk.canvasItem(mapplus$canvas);
		mapplus$setCanvas(null);
		container.removeItem(CartographyTableMenu.MAP_SLOT, 1);
		if (container.getItem(CartographyTableMenu.MAP_SLOT).isEmpty()) {
			// Returned with the other inputs by vanilla.
			container.setItem(CartographyTableMenu.MAP_SLOT, drawing);
		} else if (!serverPlayer.isAlive() || serverPlayer.hasDisconnected() || !serverPlayer.getInventory().add(drawing)) {
			Block.popResource(serverPlayer.level(), serverPlayer.blockPosition(), drawing);
		}
	}

	/** With an empty map in the first slot, the result is the drawing on it, whatever the second slot holds. */
	@Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
	private void mapplus$showCanvas(Container changed, CallbackInfo ci) {
		if (!mapplus$desk || !container.getItem(CartographyTableMenu.MAP_SLOT).is(Items.MAP)) {
			return;
		}
		ci.cancel();
		// Runs on the server only; the client shows the result the server sends.
		access.execute((level, pos) -> {
			ItemStack drawing = mapplus$canvas == null ? ItemStack.EMPTY : DrawingDesk.canvasItem(mapplus$canvas);
			if (!ItemStack.matches(drawing, resultContainer.getItem(CartographyTableMenu.RESULT_SLOT))) {
				resultContainer.setItem(CartographyTableMenu.RESULT_SLOT, drawing);
				broadcastChanges();
			}
		});
	}

	@Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
	private void mapplus$deskResults(ItemStack map, ItemStack additional, ItemStack result, CallbackInfo ci) {
		boolean book = additional.is(Items.WRITABLE_BOOK);
		boolean seal = additional.is(Items.HONEYCOMB) || additional.is(ItemTags.AXES);
		if (!book && !seal) {
			return;
		}
		ci.cancel();
		if (!mapplus$desk) {
			return;
		}
		access.execute((level, pos) -> {
			ItemStack out = book ? Atlas.stitch(additional, map, level.registryAccess()) : sealResult(level, map, additional);
			if (!ItemStack.matches(out, result)) {
				resultContainer.setItem(CartographyTableMenu.RESULT_SLOT, out);
				broadcastChanges();
			}
		});
	}

	/** Honeycomb seals a map's ink and an axe scrapes the seal off; the result is the same map. */
	@Unique
	private static ItemStack sealResult(Level level, ItemStack map, ItemStack tool) {
		MapId mapId = map.get(DataComponents.MAP_ID);
		if (mapId == null || !map.is(Items.FILLED_MAP) || level.getServer() == null) {
			return ItemStack.EMPTY;
		}
		boolean sealed = InkData.get(level.getServer()).isSealed(mapId);
		boolean sealing = tool.is(Items.HONEYCOMB);
		return sealed != sealing ? map.copyWithCount(1) : ItemStack.EMPTY;
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void mapplus$quickMoveToDesk(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (!mapplus$desk || index <= CartographyTableMenu.RESULT_SLOT) {
			return;
		}
		Slot slot = slots.get(index);
		ItemStack stack = slot.getItem();
		if (stack.isEmpty() || !(stack.is(Items.WRITABLE_BOOK) || InkMaterial.fitsTable(stack))) {
			return;
		}
		ItemStack copy = stack.copy();
		if (!moveItemStackTo(stack, CartographyTableMenu.ADDITIONAL_SLOT, CartographyTableMenu.ADDITIONAL_SLOT + 1, false)) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		}
		slot.setChanged();
		if (stack.getCount() == copy.getCount()) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		slot.onTake(player, stack);
		broadcastChanges();
		cir.setReturnValue(copy);
	}
}
