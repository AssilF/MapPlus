package com.assilf.mapplus.nation;

import com.assilf.mapplus.MapPlus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Every border post in the world. Saved as {@code data/mapplus/nations.dat};
 * the banners themselves stay ordinary vanilla blocks.
 */
public final class NationData extends SavedData {
	private static final Codec<NationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Post.CODEC.listOf().optionalFieldOf("posts", List.of()).forGetter(data -> data.posts)
	).apply(instance, NationData::new));

	private static final SavedDataType<NationData> TYPE = new SavedDataType<>(
			MapPlus.id("nations"),
			NationData::new,
			CODEC,
			null
	);

	private final List<Post> posts = new ArrayList<>();
	/** Changes whenever a post is placed or taken down, so territories are worked out again. */
	private int version = 1;

	public NationData() {
	}

	private NationData(List<Post> posts) {
		this.posts.addAll(posts);
	}

	public static NationData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public List<Post> posts() {
		return posts;
	}

	public int version() {
		return version;
	}

	/** Puts up a post, in place of any post already standing there. */
	public void put(Post post) {
		posts.removeIf(old -> old.dimension().equals(post.dimension()) && old.pos().equals(post.pos()));
		posts.add(post);
		changed();
	}

	public void remove(ResourceKey<Level> dimension, BlockPos pos) {
		if (posts.removeIf(post -> post.dimension().equals(dimension) && post.pos().equals(pos))) {
			changed();
		}
	}

	/**
	 * The name of a design's nation: the name on its most recently placed post.
	 */
	public String nameOf(String design) {
		for (int i = posts.size() - 1; i >= 0; i--) {
			Post post = posts.get(i);
			if (post.design().equals(design) && !post.name().isEmpty()) {
				return post.name();
			}
		}
		return "";
	}

	private void changed() {
		version++;
		setDirty();
	}
}
