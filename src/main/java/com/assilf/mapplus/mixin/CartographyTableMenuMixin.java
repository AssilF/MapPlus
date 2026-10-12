package com.assilf.mapplus.mixin;

import com.assilf.mapplus.atlas.Atlas;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Binding maps into atlases: a filled map plus a book and quill (or an atlas)
 * gives an atlas with that map as a new page. Taking the result uses up both
 * inputs, as with any cartography table result.
 */
@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuMixin {
	@Shadow
	@Final
	private ContainerLevelAccess access;

	@Shadow
	@Final
	private ResultContainer resultContainer;

	@Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
	private void mapplus$bindIntoAtlas(ItemStack map, ItemStack additional, ItemStack result, CallbackInfo ci) {
		if (!additional.is(Items.WRITABLE_BOOK)) {
			return;
		}
		ci.cancel();
		access.execute((level, pos) -> {
			ItemStack atlasStack = bind(map, additional);
			if (!ItemStack.matches(atlasStack, result)) {
				resultContainer.setItem(CartographyTableMenu.RESULT_SLOT, atlasStack);
				((AbstractContainerMenu) (Object) this).broadcastChanges();
			}
		});
	}

	private static ItemStack bind(ItemStack map, ItemStack book) {
		MapId mapId = map.get(DataComponents.MAP_ID);
		if (mapId == null || !map.is(Items.FILLED_MAP)) {
			return ItemStack.EMPTY;
		}
		Optional<Atlas> existing = Atlas.read(book);
		if (existing.isPresent()) {
			Atlas atlas = existing.get();
			if (atlas.contains(mapId) || atlas.plates().size() >= Atlas.MAX_PLATES) {
				return ItemStack.EMPTY;
			}
			ItemStack out = book.copyWithCount(1);
			atlas.withPlate(new Atlas.Plate(mapId.id(), false)).write(out);
			return out;
		}
		ItemStack out = book.copyWithCount(1);
		Atlas.of(mapId, false).write(out);
		if (!out.has(DataComponents.CUSTOM_NAME)) {
			out.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("item.mapplus.atlas", "Atlas"));
		}
		return out;
	}
}
