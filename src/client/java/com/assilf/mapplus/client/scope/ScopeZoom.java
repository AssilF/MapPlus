package com.assilf.mapplus.client.scope;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/** Scrolling while looking through a spyglass zooms it in and out. */
public final class ScopeZoom {
	/** How much narrower or wider than vanilla's the spyglass's view can be. */
	private static final float CLOSEST = 0.25F;
	private static final float WIDEST = 2.5F;
	private static final float STEP = 1.25F;

	private static float zoom = 1.0F;

	private ScopeZoom() {
	}

	/** Scrolling up zooms in, down zooms out, with the spyglass's own sound of drawing out or folding. */
	public static void scroll(double amount) {
		float next = Mth.clamp(amount > 0 ? zoom / STEP : zoom * STEP, CLOSEST, WIDEST);
		if (next == zoom) {
			return;
		}
		zoom = next;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
				amount > 0 ? SoundEvents.SPYGLASS_USE : SoundEvents.SPYGLASS_STOP_USING, 1.0F + (1.0F - zoom) * 0.3F, 0.6F));
	}

	/** The view's width while scoping, zoomed; back to vanilla's once the spyglass is lowered. */
	public static float apply(AbstractClientPlayer player, float modifier) {
		if (!player.isScoping()) {
			zoom = 1.0F;
			return modifier;
		}
		return modifier * zoom;
	}
}
