package com.assilf.mapplus.client.marks;

import com.assilf.mapplus.ink.Mark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.ArrayList;
import java.util.List;

/** How labels look on a map: plain text in the ink's own map color, centered on its spot. */
public final class Labels {
	private Labels() {
	}

	/** The labels to draw on a map, without one being edited. */
	public static List<Mark> shown(MapId mapId) {
		List<Mark> labels = new ArrayList<>();
		for (Mark mark : ClientMarks.get(mapId)) {
			if (mark.isLabel() && !ClientMarks.isHidden(mapId.id(), mark)) {
				labels.add(mark);
			}
		}
		return labels;
	}

	public static int color(DyeColor dye) {
		return dye.getMapColor().calculateARGBColor(MapColor.Brightness.NORMAL);
	}

	/** How wide a label is on the map, in map pixels. */
	public static float width(String text, int size) {
		return Minecraft.getInstance().font.width(text) * Mark.scale(size);
	}

	/** Where a label of this size can sit so it stays on the map. */
	public static int clampX(String text, int size, int x) {
		float half = width(text, size) / 2;
		int min = (int) Math.ceil(half);
		int max = (int) Math.floor(128 - half) - 1;
		return min > max ? 64 : Math.clamp(x, min, max);
	}

	public static int clampY(int size, int y) {
		int half = (int) Math.ceil(9 * Mark.scale(size) / 2);
		return Math.clamp(y, half, 127 - half);
	}

	/** Draws a label in a GUI, with the map's top left corner at the pose's origin. */
	public static void draw(GuiGraphicsExtractor graphics, String text, int x, int y, int size, int color) {
		Font font = Minecraft.getInstance().font;
		float scale = Mark.scale(size);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 0.5F, y + 0.5F);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, -font.width(text) / 2, -4, color, false);
		graphics.pose().popMatrix();
	}
}
