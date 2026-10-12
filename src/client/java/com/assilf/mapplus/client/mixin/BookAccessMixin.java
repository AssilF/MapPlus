package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.client.atlas.AtlasSpread;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A signed atlas read in the book screen or on a lectern: map pages show their
 * map instead of their text, which is only there for players without the mod.
 */
@Mixin(BookViewScreen.BookAccess.class)
public abstract class BookAccessMixin {
	@ModifyReturnValue(method = "fromItem", at = @At("RETURN"))
	private static BookViewScreen.BookAccess mapplus$atlasPages(BookViewScreen.BookAccess access, ItemStack stack) {
		Optional<Atlas> read = Atlas.read(stack);
		if (read.isEmpty()) {
			return access;
		}
		Atlas atlas = read.get();
		List<Component> pages = new ArrayList<>(access.pages());
		while (pages.size() < atlas.requiredPages()) {
			pages.add(Component.empty());
		}
		for (Atlas.Plate plate : atlas.plates()) {
			if (plate.page() >= 0 && plate.page() < pages.size()) {
				pages.set(plate.page(), Component.empty());
			}
		}
		BookViewScreen.BookAccess atlasAccess = new BookViewScreen.BookAccess(pages);
		AtlasSpread.remember(atlasAccess, stack);
		return atlasAccess;
	}
}
