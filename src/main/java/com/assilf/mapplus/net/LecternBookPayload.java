package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server tells a player with the mod what a lectern near them holds, so its
 * book can be shown open on it: vanilla never sends a lectern's book. An empty
 * book with {@code keep} means only the page turned; without it, the book is
 * gone.
 */
public record LecternBookPayload(BlockPos pos, int page, boolean keep, ItemStack book) implements CustomPacketPayload {
	public static final Type<LecternBookPayload> TYPE = new Type<>(MapPlus.id("lectern_book"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LecternBookPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, LecternBookPayload::pos,
			ByteBufCodecs.VAR_INT, LecternBookPayload::page,
			ByteBufCodecs.BOOL, LecternBookPayload::keep,
			ItemStack.OPTIONAL_STREAM_CODEC, LecternBookPayload::book,
			LecternBookPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
