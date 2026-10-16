package com.assilf.mapplus.ink;

import com.assilf.mapplus.MapPlusConfig;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the item in the cartography table's second slot does to the map:
 * a dye or ink sac draws, a glow ink sac makes ink glow, a water bottle erases.
 *
 * @param dye the color drawn, for {@link Kind#DYE}
 */
public record InkMaterial(Kind kind, DyeColor dye) {
	public enum Kind {
		DYE,
		GLOW,
		WATER
	}

	public static final InkMaterial GLOW = new InkMaterial(Kind.GLOW, null);
	public static final InkMaterial WATER = new InkMaterial(Kind.WATER, null);

	/** The material an item is, or null if it can't be used on the canvas. */
	public static InkMaterial of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		if (stack.is(Items.INK_SAC)) {
			return new InkMaterial(Kind.DYE, DyeColor.BLACK);
		}
		if (stack.is(Items.GLOW_INK_SAC)) {
			return GLOW;
		}
		if (InkItems.isWaterBottle(stack)) {
			return WATER;
		}
		for (DyeColor dye : DyeColor.values()) {
			if (stack.is(InkItems.dyeItem(dye))) {
				return new InkMaterial(Kind.DYE, dye);
			}
		}
		return null;
	}

	/** Items the cartography table accepts in its second slot besides vanilla's paper, map and glass pane. */
	public static boolean fitsTable(ItemStack stack) {
		return of(stack) != null || stack.is(Items.HONEYCOMB) || stack.is(ItemTags.AXES);
	}

	/** What an ink pixel becomes under this material at full strength. */
	public byte apply(byte ink) {
		return apply(ink, Ink.FULL);
	}

	/**
	 * What an ink pixel becomes under this material, used at a strength. Returns
	 * the pixel itself where nothing changes.
	 * <ul>
	 * <li>a dye colors the pixel at that strength; over its own color it only
	 * ever makes the ink stronger, since only water makes ink fainter</li>
	 * <li>glow ink makes ink glow, at the strength it has</li>
	 * <li>water at full strength erases; watered down, it fades ink one step</li>
	 * </ul>
	 */
	public byte apply(byte ink, int strength) {
		boolean present = Ink.isPresent(ink);
		return switch (kind) {
			// Re-coloring keeps the glow, as dyeing a glowing sign does.
			case DYE -> Ink.encode(dye, present && Ink.isGlow(ink),
					present && Ink.dye(ink) == dye ? Math.min(Ink.strength(ink), strength) : strength);
			case GLOW -> present ? Ink.encode(Ink.dye(ink), true, Ink.strength(ink)) : ink;
			case WATER -> strength == Ink.FULL ? Ink.NONE : Ink.fade(ink);
		};
	}

	/**
	 * What changing one pixel costs, in quarters of a pixel's worth: watered
	 * down dye goes further, while glow ink and water cost a whole pixel's worth.
	 */
	public int quarters(int strength) {
		return kind == Kind.DYE ? 4 - Ink.clampStrength(strength) : 4;
	}

	/** Pixels one item of this material covers. */
	public int perItem() {
		MapPlusConfig config = MapPlusConfig.get();
		return Math.max(1, kind == Kind.WATER ? config.waterPerBottle : config.inkPerItem);
	}

	/** Ink of this material the player has loaded but not used yet. */
	public int charge(InkSupply supply) {
		return switch (kind) {
			case DYE -> supply.dye(dye);
			case GLOW -> supply.glow();
			case WATER -> supply.water();
		};
	}

	public InkSupply withCharge(InkSupply supply, int amount) {
		return switch (kind) {
			case DYE -> supply.withDye(dye, amount);
			case GLOW -> supply.withGlow(amount);
			case WATER -> supply.withWater(amount);
		};
	}
}
