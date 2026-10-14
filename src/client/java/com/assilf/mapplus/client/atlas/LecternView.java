package com.assilf.mapplus.client.atlas;

import com.assilf.mapplus.net.LecternBookPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Books lying open on lecterns: what the server says each lectern near the
 * player holds, drawn open at its page across the lectern's slope, in place of
 * the little closed-looking book, for whoever comes close enough to read it.
 */
public final class LecternView {
	/** How close a lectern's book is drawn open, in blocks. */
	private static final double READABLE = 12.0;
	/** The slope of a lectern's top. */
	private static final double SLOPE = Math.toRadians(22.5);
	/** How wide the open book lies across the lectern, in blocks. */
	private static final float WIDTH = 0.875F;
	/** The middle of the lectern's top, and how far above it the book lies. */
	private static final double MIDDLE_Y = 1.0;
	private static final double ABOVE = 0.02;

	/** A lectern's book, and the page it's open at. */
	public record Shown(ItemStack book, int page) {
	}

	private static final Map<BlockPos, Shown> BOOKS = new HashMap<>();
	private static ClientLevel level;

	private LecternView() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LecternBookPayload.TYPE, (payload, context) -> accept(payload));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != level) {
				level = client.level;
				BOOKS.clear();
			}
		});
	}

	private static void accept(LecternBookPayload payload) {
		if (payload.keep()) {
			Shown shown = BOOKS.get(payload.pos());
			if (shown != null) {
				BOOKS.put(payload.pos(), new Shown(shown.book(), payload.page()));
			}
		} else if (payload.book().isEmpty()) {
			BOOKS.remove(payload.pos());
		} else {
			BOOKS.put(payload.pos().immutable(), new Shown(payload.book(), payload.page()));
		}
	}

	/** What a lectern holds, if the server said. */
	public static Shown at(BlockPos pos) {
		return BOOKS.get(pos);
	}

	/**
	 * Draws a lectern's book open on it, in the lectern's own block space.
	 *
	 * @param facing the way the lectern faces, toward its reader
	 * @return whether it was drawn; if not, the lectern shows its usual book
	 */
	public static boolean submit(Shown shown, BlockPos pos, Direction facing, Vec3 camera, PoseStack pose, SubmitNodeCollector collector, int light) {
		if (camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) > READABLE * READABLE) {
			return false;
		}
		double fx = facing.getStepX();
		double fz = facing.getStepZ();
		double cos = Math.cos(SLOPE);
		double sin = Math.sin(SLOPE);
		// Across the page, to the reader's right; down the page, toward the reader and down the slope; and into it.
		double[] across = {fz, 0, -fx};
		double[] down = {fx * cos, -sin, fz * cos};
		double[] into = {-fx * sin, -cos, -fz * sin};
		float scale = WIDTH / (OpenBook.MAX_X - OpenBook.MIN_X);
		float middleX = (OpenBook.MIN_X + OpenBook.MAX_X) / 2;
		float middleY = (OpenBook.MIN_Y + OpenBook.MAX_Y) / 2;
		double[] origin = new double[3];
		for (int i = 0; i < 3; i++) {
			double middle = i == 1 ? MIDDLE_Y : 0.5;
			origin[i] = middle - into[i] * ABOVE - (across[i] * middleX + down[i] * middleY) * scale;
		}
		Matrix4f frame = new Matrix4f(
				(float) across[0] * scale, (float) across[1] * scale, (float) across[2] * scale, 0,
				(float) down[0] * scale, (float) down[1] * scale, (float) down[2] * scale, 0,
				(float) into[0] * scale, (float) into[1] * scale, (float) into[2] * scale, 0,
				(float) origin[0], (float) origin[1], (float) origin[2], 1);
		pose.pushPose();
		pose.last().pose().mul(frame);
		OpenBook.submit(pose, collector, light, shown.book(), shown.page());
		pose.popPose();
		return true;
	}
}
