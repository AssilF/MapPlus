package com.assilf.mapplus.ink;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Labels and pins as vanilla map markers. A pin is a banner marker, exactly as
 * vanilla shows a banner in the world, named after the banner. Players without
 * the mod also see each label as a banner marker in its ink, named with its
 * text, the only way vanilla shows text on a map.
 */
public final class MarkDecorations {
	/** Banner markers point down, as vanilla's do. */
	private static final byte BANNER_ROTATION = 8;

	private MarkDecorations() {
	}

	/** A map's markers with its pins added, and its labels too if the player can't show them as text. */
	public static List<MapDecoration> withMarks(List<MapDecoration> decorations, List<Mark> marks, boolean labelsAsBanners) {
		List<MapDecoration> out = new ArrayList<>(decorations);
		for (Mark mark : marks) {
			if (mark.isLabel() && !labelsAsBanners) {
				continue;
			}
			Optional<Component> name = mark.text().isEmpty() ? Optional.empty() : Optional.of(Component.literal(mark.text()));
			out.add(new MapDecoration(banner(mark.color()), coordinate(mark.x()), coordinate(mark.y()), BANNER_ROTATION, name));
		}
		return out;
	}

	/** A map pixel as a marker coordinate, which counts half pixels from the map's center. */
	public static byte coordinate(int pixel) {
		return (byte) Math.clamp(pixel * 2 - Ink.SIZE + 1, -128, 127);
	}

	public static Holder<MapDecorationType> banner(DyeColor color) {
		return switch (color) {
			case WHITE -> MapDecorationTypes.WHITE_BANNER;
			case ORANGE -> MapDecorationTypes.ORANGE_BANNER;
			case MAGENTA -> MapDecorationTypes.MAGENTA_BANNER;
			case LIGHT_BLUE -> MapDecorationTypes.LIGHT_BLUE_BANNER;
			case YELLOW -> MapDecorationTypes.YELLOW_BANNER;
			case LIME -> MapDecorationTypes.LIME_BANNER;
			case PINK -> MapDecorationTypes.PINK_BANNER;
			case GRAY -> MapDecorationTypes.GRAY_BANNER;
			case LIGHT_GRAY -> MapDecorationTypes.LIGHT_GRAY_BANNER;
			case CYAN -> MapDecorationTypes.CYAN_BANNER;
			case PURPLE -> MapDecorationTypes.PURPLE_BANNER;
			case BLUE -> MapDecorationTypes.BLUE_BANNER;
			case BROWN -> MapDecorationTypes.BROWN_BANNER;
			case GREEN -> MapDecorationTypes.GREEN_BANNER;
			case RED -> MapDecorationTypes.RED_BANNER;
			case BLACK -> MapDecorationTypes.BLACK_BANNER;
		};
	}
}
