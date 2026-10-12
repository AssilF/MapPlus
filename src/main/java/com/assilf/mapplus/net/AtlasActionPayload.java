package com.assilf.mapplus.net;

import com.assilf.mapplus.MapPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/**
 * Client changes the atlas it holds.
 *
 * @param page  the map page the action applies to
 * @param mapId the map the client sees on that page; the action is ignored if
 *              the page now holds a different map (the atlas changed meanwhile)
 * @param arg   for {@link Action#BOOKMARK}, the dye id of the ribbon, or -1 to remove it
 */
public record AtlasActionPayload(boolean offhand, Action action, int page, int mapId, int arg) implements CustomPacketPayload {
	public enum Action {
		/** Make a map page the one that explores while the atlas is held. */
		SELECT,
		/** Add a blank sketch page, using one paper. */
		ADD_SKETCH,
		/** Take a map page out of the atlas as a filled map. */
		TAKE_OUT,
		/** Put a dye ribbon on a page, or remove it. */
		BOOKMARK
	}

	public static final Type<AtlasActionPayload> TYPE = new Type<>(MapPlus.id("atlas_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AtlasActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, AtlasActionPayload::offhand,
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal), AtlasActionPayload::action,
			ByteBufCodecs.VAR_INT, AtlasActionPayload::page,
			ByteBufCodecs.VAR_INT, AtlasActionPayload::mapId,
			ByteBufCodecs.VAR_INT, AtlasActionPayload::arg,
			AtlasActionPayload::new
	);

	public InteractionHand hand() {
		return offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
