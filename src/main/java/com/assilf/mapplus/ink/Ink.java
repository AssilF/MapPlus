package com.assilf.mapplus.ink;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.MapColor;

/**
 * One ink pixel is stored as a byte: 0 means no ink, otherwise bit 7 is set,
 * bits 5-6 hold how watered down it is, bit 4 marks glow ink and bits 0-3 hold
 * the dye id.
 */
public final class Ink {
	public static final int SIZE = 128;
	public static final int PIXELS = SIZE * SIZE;

	public static final byte NONE = 0;
	private static final int PRESENT = 0x80;
	private static final int GLOW = 0x10;
	private static final int DYE_MASK = 0x0F;
	private static final int STRENGTH_SHIFT = 5;
	private static final int STRENGTH_MASK = 0x60;

	/**
	 * How watered down ink is, from {@link #FULL} to {@link #FAINTEST}: full,
	 * three quarters, half and a quarter of its color.
	 */
	public static final int FULL = 0;
	public static final int FAINTEST = 3;

	private Ink() {
	}

	public static byte encode(DyeColor dye, boolean glow) {
		return encode(dye, glow, FULL);
	}

	public static byte encode(DyeColor dye, boolean glow, int strength) {
		return (byte) (PRESENT | clampStrength(strength) << STRENGTH_SHIFT | (glow ? GLOW : 0) | (dye.getId() & DYE_MASK));
	}

	public static int clampStrength(int strength) {
		return Math.clamp(strength, FULL, FAINTEST);
	}

	public static int strength(byte ink) {
		return (ink & STRENGTH_MASK) >> STRENGTH_SHIFT;
	}

	/** Ink watered down one more step, or no ink once it's past the faintest. */
	public static byte fade(byte ink) {
		if (!isPresent(ink)) {
			return ink;
		}
		int strength = strength(ink) + 1;
		return strength > FAINTEST ? NONE : encode(dye(ink), isGlow(ink), strength);
	}

	/** How much of its color ink at this strength shows, over what's under it. */
	public static float opacity(int strength) {
		return 1.0F - clampStrength(strength) * 0.25F;
	}

	/**
	 * Whether watered ink shows on this pixel, for players who can only be sent
	 * map colors: it shows on its share of a fixed 2 by 2 pattern, as vanilla
	 * shades water, and the map shows through the rest.
	 */
	public static boolean showsAt(int strength, int x, int y) {
		return switch (clampStrength(strength)) {
			case FULL -> true;
			case 1 -> (x & 1) == 0 || (y & 1) == 0;
			case 2 -> ((x + y) & 1) == 0;
			default -> (x & 1) == 0 && (y & 1) == 0;
		};
	}

	public static boolean isPresent(byte ink) {
		return (ink & PRESENT) != 0;
	}

	public static boolean isGlow(byte ink) {
		return (ink & GLOW) != 0;
	}

	public static DyeColor dye(byte ink) {
		return DyeColor.byId(ink & DYE_MASK);
	}

	/**
	 * The vanilla packed map color an ink pixel is shown as. Glow ink uses the
	 * brightest shade so it stands out from regular ink.
	 */
	public static byte toMapColor(byte ink) {
		MapColor color = dye(ink).getMapColor();
		return color.getPackedId(brightness(ink));
	}

	public static MapColor.Brightness brightness(byte ink) {
		return isGlow(ink) ? MapColor.Brightness.HIGH : MapColor.Brightness.NORMAL;
	}
}
