package com.assilf.mapplus.ink;

import com.assilf.mapplus.MapPlus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.item.DyeColor;

import java.util.Arrays;
import java.util.List;

/**
 * Ink a player has loaded but not used yet: one amount per dye, plus glow ink
 * and water. Stored as a Fabric attachment on the player, which vanilla simply
 * drops if the mod is removed.
 */
public record InkSupply(int[] dyes, int glow, int water) {
	public static final int DYES = DyeColor.values().length;

	public static final InkSupply EMPTY = new InkSupply(new int[DYES], 0, 0);

	public static final Codec<InkSupply> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.listOf().optionalFieldOf("dyes", List.of()).forGetter(supply -> Arrays.stream(supply.dyes).boxed().toList()),
			Codec.INT.optionalFieldOf("glow", 0).forGetter(InkSupply::glow),
			Codec.INT.optionalFieldOf("water", 0).forGetter(InkSupply::water)
	).apply(instance, (dyes, glow, water) -> {
		int[] amounts = new int[DYES];
		for (int i = 0; i < Math.min(DYES, dyes.size()); i++) {
			amounts[i] = Math.max(0, dyes.get(i));
		}
		return new InkSupply(amounts, Math.max(0, glow), Math.max(0, water));
	}));

	public static final AttachmentType<InkSupply> ATTACHMENT = AttachmentRegistry.create(
			MapPlus.id("ink_supply"),
			builder -> builder.persistent(CODEC).copyOnDeath().initializer(() -> EMPTY)
	);

	public int dye(DyeColor dye) {
		return dyes[dye.getId()];
	}

	public InkSupply withDye(DyeColor dye, int amount) {
		int[] copy = dyes.clone();
		copy[dye.getId()] = amount;
		return new InkSupply(copy, glow, water);
	}

	public InkSupply withGlow(int amount) {
		return new InkSupply(dyes, amount, water);
	}

	public InkSupply withWater(int amount) {
		return new InkSupply(dyes, glow, amount);
	}

	/** Forces the attachment class to load so the type is registered at startup. */
	public static void init() {
	}
}
