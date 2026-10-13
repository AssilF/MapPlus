package com.assilf.mapplus.ink;

import com.assilf.mapplus.MapPlusConfig;
import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.atlas.Sketch;
import com.assilf.mapplus.net.SketchPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;

import java.util.BitSet;

/**
 * Sketching in a book and quill: with the book in one hand and ink in the
 * other, the book's own quill draws on an empty page. The ink is paid for as
 * at the cartography table, from the hand holding it. A sketch is kept in the
 * book; signing it freezes it like the text.
 */
public final class Sketching {
	private Sketching() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(SketchPayload.TYPE, (payload, context) -> draw(context.player(), payload));
	}

	/** Whether a page of a book can hold a sketch: any page but a map's, written on or not. */
	public static boolean canSketch(ItemStack book, Atlas atlas, int page) {
		return page >= 0 && page < WritableBookContent.MAX_PAGES && atlas.plateAt(page) == null;
	}

	public static void draw(Player player, SketchPayload payload) {
		MapPlusConfig config = MapPlusConfig.get();
		ItemStack book = player.getItemInHand(payload.hand());
		if (!config.sketching || !book.is(Items.WRITABLE_BOOK) || player.isSpectator()) {
			return;
		}
		Atlas atlas = Atlas.read(book).orElse(Atlas.EMPTY);
		Sketch old = atlas.sketchAt(payload.page());
		if (!canSketch(book, atlas, payload.page()) || old == null && (payload.erase() || atlas.sketches().size() >= config.sketchPages)) {
			return;
		}
		byte[] ink = old == null ? new byte[Sketch.PIXELS] : old.ink().clone();
		BitSet pixels = payload.pixels();
		boolean changed = false;
		if (payload.erase()) {
			for (int i = pixels.nextSetBit(0); i >= 0 && i < Sketch.PIXELS; i = pixels.nextSetBit(i + 1)) {
				changed |= ink[i] != Ink.NONE;
				ink[i] = Ink.NONE;
			}
		} else {
			InteractionHand inkHand = payload.hand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			InkMaterial material = InkMaterial.of(player.getItemInHand(inkHand));
			if (material == null) {
				return;
			}
			int strength = payload.strength();
			int quarters = material.quarters(strength);
			InkManager.Wallet wallet = new InkManager.Wallet(player, material, InkManager.InkSource.of(player, inkHand));
			int credit = 0;
			for (int i = pixels.nextSetBit(0); i >= 0 && i < Sketch.PIXELS; i = pixels.nextSetBit(i + 1)) {
				byte next = material.apply(ink[i], strength);
				if (next == ink[i]) {
					continue;
				}
				if (credit < quarters) {
					if (!wallet.spend()) {
						break;
					}
					credit += 4;
				}
				credit -= quarters;
				ink[i] = next;
				changed = true;
			}
			wallet.save();
		}
		if (changed) {
			atlas.withSketch(new Sketch(payload.page(), ink)).write(book);
		}
	}
}
