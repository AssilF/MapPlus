package com.assilf.mapplus.client.marks;

import com.assilf.mapplus.ink.Ink;
import com.assilf.mapplus.ink.Mark;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.ArrayList;
import java.util.List;

/**
 * How marks look on a map, in the world and in screens alike. Labels are text
 * in their ink; pins are their banner's flag, with its patterns; icons are a
 * sherd's picture in their ink. Pins and icons are named underneath, as
 * vanilla names its markers.
 */
public final class MarkRenderer {
	/** The see-through black behind marker names, as vanilla draws it. */
	private static final int NAME_BACKGROUND = 0x80000000;
	private static final float PICTURE_Z = -0.022F;
	private static final float NAME_Z = -0.024F;
	private static final float LABEL_Z = -0.025F;

	private MarkRenderer() {
	}

	/** The marks to draw on a map, without one being edited. */
	public static List<Mark> shown(MapId mapId) {
		List<Mark> shown = new ArrayList<>();
		for (Mark mark : ClientMarks.get(mapId)) {
			if (!ClientMarks.isHidden(mapId.id(), mark)) {
				shown.add(mark);
			}
		}
		return shown;
	}

	// Sizes, in map pixels

	public static float width(Mark mark) {
		return switch (mark.kind()) {
			case LABEL -> Labels.width(mark.text(), mark.size());
			case PIN -> pinHeight(mark.size()) / 2;
			case ICON -> iconWidth(mark.size());
		};
	}

	public static float height(Mark mark) {
		return switch (mark.kind()) {
			case LABEL -> 9 * Mark.scale(mark.size());
			case PIN -> pinHeight(mark.size());
			case ICON -> iconWidth(mark.size()) * MarkIcons.SYMBOL_HEIGHT / MarkIcons.SYMBOL_WIDTH;
		};
	}

	private static float pinHeight(int size) {
		return switch (Mark.clampSize(size)) {
			case Mark.SMALL -> 10;
			case Mark.LARGE -> 20;
			default -> 14;
		};
	}

	private static float iconWidth(int size) {
		return switch (Mark.clampSize(size)) {
			case Mark.SMALL -> 9;
			case Mark.LARGE -> 18;
			default -> 12;
		};
	}

	/** Where a pin's or icon's picture is centered: on its spot, moved in so it stays on the map. */
	private static float centerX(Mark mark) {
		float half = width(mark) / 2;
		return Mth.clamp(mark.x() + 0.5F, half, Ink.SIZE - half);
	}

	private static float centerY(Mark mark) {
		float half = height(mark) / 2;
		return Mth.clamp(mark.y() + 0.5F, half, Ink.SIZE - half);
	}

	/** A label's or icon's ink, as see-through as it's watered down; glowing ink in its brightest shade. */
	public static int inkColor(Mark mark) {
		int alpha = Math.round(Ink.opacity(mark.fade()) * 255);
		int color = mark.glow()
				? mark.color().getMapColor().calculateARGBColor(net.minecraft.world.level.material.MapColor.Brightness.HIGH)
				: Labels.color(mark.color());
		return alpha << 24 | color & 0xFFFFFF;
	}

	private static int darker(int color) {
		return (color >> 16 & 0xFF) * 2 / 5 << 16 | (color >> 8 & 0xFF) * 2 / 5 << 8 | (color & 0xFF) * 2 / 5;
	}

	private static Identifier picture(Mark mark) {
		return mark.kind() == Mark.Kind.PIN ? MarkIcons.banner(mark) : MarkIcons.symbol(mark.symbol());
	}

	private static int pictureColor(Mark mark) {
		return mark.kind() == Mark.Kind.PIN ? 0xFFFFFFFF : inkColor(mark);
	}

	private static int pictureWidth(Mark mark) {
		return mark.kind() == Mark.Kind.PIN ? MarkIcons.FLAG_WIDTH : MarkIcons.SYMBOL_WIDTH;
	}

	private static int pictureHeight(Mark mark) {
		return mark.kind() == Mark.Kind.PIN ? MarkIcons.FLAG_HEIGHT : MarkIcons.SYMBOL_HEIGHT;
	}

	/** How big a marker's name is drawn, as vanilla sizes them: smaller the longer it is. */
	private static float nameScale(int width) {
		return Mth.clamp(25.0F / width, 0.0F, 6.0F / 9.0F);
	}

	// In screens, with the map's top left corner at the pose's origin

	public static void draw(GuiGraphicsExtractor graphics, Mark mark) {
		if (mark.isLabel()) {
			Labels.draw(graphics, mark.text(), mark.x(), mark.y(), mark.size(), inkColor(mark));
			return;
		}
		float width = width(mark);
		float height = height(mark);
		float x = centerX(mark) - width / 2;
		float y = centerY(mark) - height / 2;
		Identifier texture = picture(mark);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (texture != null) {
			int textureWidth = pictureWidth(mark);
			int textureHeight = pictureHeight(mark);
			graphics.pose().scale(width / textureWidth, height / textureHeight);
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight,
					pictureColor(mark));
		} else {
			// The game's textures couldn't be read; a dot in the mark's color still shows where it is.
			graphics.pose().scale(width, height);
			graphics.fill(0, 0, 1, 1, 0xFF000000 | Labels.color(mark.color()));
		}
		graphics.pose().popMatrix();

		if (!mark.text().isEmpty()) {
			Font font = Minecraft.getInstance().font;
			int textWidth = font.width(mark.text());
			float scale = nameScale(textWidth);
			graphics.pose().pushMatrix();
			graphics.pose().translate(centerX(mark) - textWidth * scale / 2, y + height + 1);
			graphics.pose().scale(scale, scale);
			graphics.fill(-1, -1, textWidth, 9, NAME_BACKGROUND);
			graphics.text(font, mark.text(), 0, 0, 0xFFFFFFFF, false);
			graphics.pose().popMatrix();
		}
	}

	// In the world, with the map's top left corner at the pose's origin

	public static void submit(Mark mark, PoseStack pose, SubmitNodeCollector collector, int light) {
		Font font = Minecraft.getInstance().font;
		if (mark.isLabel()) {
			FormattedCharSequence text = Component.literal(mark.text()).getVisualOrderText();
			Labels.Fit fit = Labels.fit(mark.text(), mark.size(), mark.x(), mark.y());
			pose.pushPose();
			pose.translate(fit.x(), fit.y(), LABEL_Z);
			pose.scale(fit.scale(), fit.scale(), -1.0F);
			pose.translate(0.0F, 0.0F, 0.1F);
			// Glowing ink is outlined in its own darker shade, as glowing sign text is.
			int outline = mark.glow() ? 0xFF000000 | darker(inkColor(mark)) : 0;
			collector.order(1).submitText(pose, -font.width(text) / 2.0F, -4.0F, text, false, Font.DisplayMode.NORMAL, light,
					inkColor(mark), 0, outline);
			pose.popPose();
			return;
		}
		float width = width(mark);
		float height = height(mark);
		float x0 = centerX(mark) - width / 2;
		float y0 = centerY(mark) - height / 2;
		float x1 = x0 + width;
		float y1 = y0 + height;
		Identifier texture = picture(mark);
		if (texture != null) {
			int color = pictureColor(mark);
			collector.order(1).submitCustomGeometry(pose, RenderTypes.text(texture), (at, vertices) -> {
				vertices.addVertex(at, x0, y1, PICTURE_Z).setColor(color).setUv(0.0F, 1.0F).setLight(light);
				vertices.addVertex(at, x1, y1, PICTURE_Z).setColor(color).setUv(1.0F, 1.0F).setLight(light);
				vertices.addVertex(at, x1, y0, PICTURE_Z).setColor(color).setUv(1.0F, 0.0F).setLight(light);
				vertices.addVertex(at, x0, y0, PICTURE_Z).setColor(color).setUv(0.0F, 0.0F).setLight(light);
			});
		}
		if (!mark.text().isEmpty()) {
			FormattedCharSequence text = Component.literal(mark.text()).getVisualOrderText();
			int textWidth = font.width(text);
			float scale = nameScale(textWidth);
			pose.pushPose();
			pose.translate(centerX(mark) - textWidth * scale / 2, y1 + 1, NAME_Z);
			pose.scale(scale, scale, -1.0F);
			pose.translate(0.0F, 0.0F, 0.1F);
			collector.order(1).submitText(pose, 0.0F, 0.0F, text, false, Font.DisplayMode.NORMAL, light, 0xFFFFFFFF, NAME_BACKGROUND, 0);
			pose.popPose();
		}
	}
}
