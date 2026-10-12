package com.assilf.mapplus.ink;

import net.minecraft.core.component.DataComponents;
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

	public static Item dyeItem(DyeColor dye) {
		return switch (dye) {
			case WHITE -> Items.WHITE_DYE;
			case ORANGE -> Items.ORANGE_DYE;
			case MAGENTA -> Items.MAGENTA_DYE;
			case LIGHT_BLUE -> Items.LIGHT_BLUE_DYE;
			case YELLOW -> Items.YELLOW_DYE;
			case LIME -> Items.LIME_DYE;
			case PINK -> Items.PINK_DYE;
			case GRAY -> Items.GRAY_DYE;
			case LIGHT_GRAY -> Items.LIGHT_GRAY_DYE;
			case CYAN -> Items.CYAN_DYE;
			case PURPLE -> Items.PURPLE_DYE;
			case BLUE -> Items.BLUE_DYE;
			case BROWN -> Items.BROWN_DYE;
			case GREEN -> Items.GREEN_DYE;
			case RED -> Items.RED_DYE;
			case BLACK -> Items.BLACK_DYE;
		};
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
