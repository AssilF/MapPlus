package com.assilf.mapplus.client.frame;

import com.assilf.mapplus.ink.FramePainting;
import com.assilf.mapplus.net.FramePaintPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Painting a framed map smoothly: while use is held on one with a pen and ink,
 * where the crosshair is goes to the server every tick, and the server joins
 * the points into a stroke. Vanilla's own repeated use, five times a second,
 * still paints for players without the mod.
 */
public final class FramePainter {
	private FramePainter() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FramePainter::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.gui.screen() != null || !minecraft.options.keyUse.isDown()
				|| !(minecraft.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof ItemFrame frame)
				|| !frame.getItem().is(Items.FILLED_MAP) || FramePainting.penHand(minecraft.player) == null
				|| !ClientPlayNetworking.canSend(FramePaintPayload.TYPE)) {
			return;
		}
		ClientPlayNetworking.send(new FramePaintPayload(frame.getId(), hit.getLocation().x, hit.getLocation().y, hit.getLocation().z));
	}
}
