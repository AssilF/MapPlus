package com.assilf.mapplus.ink;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.MapColor;

/**
 * One ink pixel is stored as a byte: 0 means no ink, otherwise bit 7 is set,
 * bit 4 marks glow ink and bits 0-3 hold the dye id.
 */
public final class Ink {
	public static final int SIZE = 128;
	public static final int PIXELS = SIZE * SIZE;

	public static final byte NONE = 0;
	private static final int PRESENT = 0x80;
	private static final int GLOW = 0x10;
	private static final int DYE_MASK = 0x0F;

	private Ink() {
	}

	public static byte encode(DyeColor dye, boolean glow) {
		return (byte) (PRESENT | (glow ? GLOW : 0) | (dye.getId() & DYE_MASK));
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
		return color.getPackedId(isGlow(ink) ? MapColor.Brightness.HIGH : MapColor.Brightness.NORMAL);
	}
}
