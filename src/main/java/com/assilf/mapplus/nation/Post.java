package com.assilf.mapplus.nation;

import com.assilf.mapplus.ink.Mark;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A named banner standing in the world: a border post of the nation whose flag
 * it is. A nation is a banner design, its base color and patterns; the
 * banner's anvil name is the nation's name.
 */
public record Post(ResourceKey<Level> dimension, BlockPos pos, DyeColor color, List<Mark.Layer> layers, String name) {
	public static final Codec<Post> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Post::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(Post::pos),
			DyeColor.CODEC.fieldOf("color").forGetter(Post::color),
			Mark.Layer.CODEC.listOf().optionalFieldOf("layers", List.of()).forGetter(Post::layers),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Post::name)
	).apply(instance, Post::new));

	/** Which nation the post belongs to: banners of one design are one nation. */
	public String design() {
		StringBuilder design = new StringBuilder().append(color.getId());
		for (Mark.Layer layer : layers) {
			design.append('/').append(layer.asset()).append('=').append(layer.color().getId());
		}
		return design.toString();
	}
}
