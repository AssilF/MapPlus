package com.assilf.mapplus.ink;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Which vanilla items count as ink, glow ink and water.
 */
public final class InkItems {
	private InkItems() {
	}

	private static final Item[] DYES = new Item[DyeColor.values().length];

	public static Item dyeItem(DyeColor dye) {
		Item item = DYES[dye.getId()];
		if (item == null) {
			item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(dye.getName() + "_dye"));
			DYES[dye.getId()] = item;
		}
		return item;
	}

	/** Whether the stack can be used up for ink of this color. Ink sacs count as black. */
	public static boolean isInkFor(ItemStack stack, DyeColor dye) {
		if (stack.is(dyeItem(dye))) {
			return true;
		}
		return dye == DyeColor.BLACK && stack.is(Items.INK_SAC);
	}

	public static boolean isGlowInk(ItemStack stack) {
		return stack.is(Items.GLOW_INK_SAC);
	}

	public static boolean isWaterBottle(ItemStack stack) {
		if (!stack.is(Items.POTION)) {
			return false;
		}
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && contents.is(Potions.WATER);
	}
}
