package com.assilf.mapplus.mixin;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.ink.DrawingDesk;
import com.assilf.mapplus.ink.InkData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Taking a drawing desk result:
 * <ul>
 * <li>a drawing on an empty map uses up only that map; its ink was paid while drawing</li>
 * <li>a sealed map uses up the map and the honeycomb, as vanilla does</li>
 * <li>an unsealed map uses up the map and damages the axe instead of using it up</li>
 * <li>a map cut out of an atlas leaves the atlas on the table without it, and wears the shears</li>
 * </ul>
 * The seal changes before the inputs do, so the next result already sees it.
 */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$5")
public abstract class CartographyResultSlotMixin {
	@Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
	private void mapplus$takeDeskResult(Player player, ItemStack taken, CallbackInfo ci) {
		if (!(player.containerMenu instanceof CartographyTableMenu menu) || !DrawingDesk.of(menu).mapplus$isDesk()) {
			return;
		}
		Slot mapSlot = menu.getSlot(CartographyTableMenu.MAP_SLOT);
		Slot additionalSlot = menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT);
		ItemStack input = mapSlot.getItem();
		ItemStack additional = additionalSlot.getItem();
		Slot self = (Slot) (Object) this;

		if (DrawingDesk.isBlankCanvas(input)) {
			// Forget the canvas first, so the result doesn't come back when the map is used up.
			DrawingDesk.of(menu).mapplus$setCanvas(null);
			mapSlot.remove(1);
			mapplus$playSound(player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT);
			self.setChanged();
			ci.cancel();
			return;
		}

		if (Atlas.canCut(input) && additional.is(Items.SHEARS)) {
			// The map comes out of the atlas in place; the shears take a little wear. Only the server cuts.
			if (player.level() instanceof ServerLevel level) {
				MapId cut = taken.get(DataComponents.MAP_ID);
				if (cut == null || !Atlas.cut(input, cut, level.registryAccess())) {
					taken.setCount(0);
				} else {
					additional.hurtAndBreak(1, level, player instanceof ServerPlayer serverPlayer ? serverPlayer : null, item -> {
					});
				}
				DrawingDesk.of(menu).mapplus$cutDone();
			}
			mapSlot.setChanged();
			additionalSlot.setChanged();
			mapplus$playSound(player, SoundEvents.SHEEP_SHEAR);
			self.setChanged();
			ci.cancel();
			return;
		}

		MapId mapId = input.get(DataComponents.MAP_ID);
		if (mapId == null) {
			return;
		}
		if (additional.is(ItemTags.AXES)) {
			if (player.level() instanceof ServerLevel level) {
				InkData data = InkData.get(level.getServer());
				// Checked when the result was shown; the map comes out either way, so nothing is lost or copied.
				if (data.mayScrape(player, mapId)) {
					data.setSealed(mapId, false);
				}
				additional.hurtAndBreak(1, level, player instanceof ServerPlayer serverPlayer ? serverPlayer : null, item -> {
				});
			}
			mapSlot.remove(1);
			additionalSlot.setChanged();
			mapplus$playSound(player, SoundEvents.AXE_WAX_OFF.value());
			self.setChanged();
			ci.cancel();
		} else if (additional.is(Items.HONEYCOMB)) {
			if (player.level() instanceof ServerLevel level) {
				InkData.get(level.getServer()).seal(mapId, player.getUUID());
			}
			mapplus$playSound(player, SoundEvents.HONEYCOMB_WAX_ON);
		}
	}

	@Unique
	private static void mapplus$playSound(Player player, SoundEvent sound) {
		if (!player.level().isClientSide()) {
			player.level().playSound(null, player.blockPosition(), sound, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}
}
