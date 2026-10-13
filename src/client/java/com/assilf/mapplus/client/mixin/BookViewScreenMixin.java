package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.atlas.Sketch;
import com.assilf.mapplus.client.atlas.AtlasBookScreen;
import com.assilf.mapplus.client.atlas.AtlasSpread;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A signed atlas in the vanilla book screen (and on a lectern): each map shows
 * on the page facing its notes.
 */
@Mixin(BookViewScreen.class)
public abstract class BookViewScreenMixin extends Screen implements AtlasBookScreen {
	@Shadow
	private BookViewScreen.BookAccess bookAccess;

	@Shadow
	private int currentPage;

	@Shadow
	private PageButton forwardButton;

	@Shadow
	private PageButton backButton;

	@Unique
	private int mapplus$backOffset;

	@Unique
	private int mapplus$forwardOffset;

	@Unique
	private boolean mapplus$measured;

	protected BookViewScreenMixin(Component title) {
		super(title);
	}

	@Shadow
	private int backgroundLeft() {
		throw new AssertionError();
	}

	@Shadow
	public abstract boolean setPage(int page);

	@Unique
	private Atlas mapplus$atlas() {
		return Atlas.read(AtlasSpread.source(bookAccess)).orElse(null);
	}

	@Unique
	private AtlasSpread.Layout mapplus$layout() {
		return AtlasSpread.layout(mapplus$atlas(), currentPage, bookAccess.getPageCount());
	}

	@ModifyReturnValue(method = "backgroundLeft", at = @At("RETURN"))
	private int mapplus$centerSpread(int left) {
		AtlasSpread.Layout layout = mapplus$layout();
		return layout == null ? left : left + layout.shift();
	}

	@Inject(method = "createPageControlButtons", at = @At("TAIL"))
	private void mapplus$measureButtons(CallbackInfo ci) {
		int left = backgroundLeft();
		mapplus$backOffset = backButton.getX() - left;
		mapplus$forwardOffset = forwardButton.getX() - left;
		mapplus$measured = true;
	}

	/** A held atlas opens on the notes facing its first map. A lectern's page is shared, so it is left alone. */
	@Inject(method = "init", at = @At("TAIL"))
	private void mapplus$openOnNotes(CallbackInfo ci) {
		Atlas atlas = mapplus$atlas();
		if (atlas != null && !((Object) this instanceof LecternScreen) && !AtlasSpread.isStop(atlas, currentPage, bookAccess.getPageCount())) {
			setPage(AtlasSpread.firstStop(atlas, currentPage, bookAccess.getPageCount()));
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void mapplus$beforeExtract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (mapplus$measured && mapplus$atlas() != null) {
			int left = backgroundLeft();
			backButton.setX(left + mapplus$backOffset);
			forwardButton.setX(left + mapplus$forwardOffset);
		}
	}

	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void mapplus$drawSpread(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y,
			float u, float v, int width, int height, int textureWidth, int textureHeight, Operation<Void> original) {
		AtlasSpread.Layout layout = mapplus$layout();
		Atlas atlas = mapplus$atlas();
		if (layout == null) {
			original.call(graphics, pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
		} else {
			AtlasSpread.drawPage(graphics, texture, x, y, layout.mirrored());
			AtlasSpread.drawSpread(graphics, layout, atlas, x, y, bookAccess::getPage, index -> {
				Sketch sketch = atlas.sketchAt(index);
				return sketch == null ? null : sketch.ink();
			});
		}
		Sketch sketch = atlas == null ? null : atlas.sketchAt(currentPage);
		if (sketch != null) {
			AtlasSpread.drawSketch(graphics, sketch.ink(), x, y, false);
		}
	}

	@Override
	public int mapplus$turnTarget(boolean forward) {
		Atlas atlas = mapplus$atlas();
		if (atlas == null) {
			return -2;
		}
		return AtlasSpread.turnTarget(atlas, currentPage, bookAccess.getPageCount(), forward);
	}

	@Inject(method = "pageForward", at = @At("HEAD"), cancellable = true)
	private void mapplus$turnForward(CallbackInfo ci) {
		mapplus$turn(true, ci);
	}

	@Inject(method = "pageBack", at = @At("HEAD"), cancellable = true)
	private void mapplus$turnBack(CallbackInfo ci) {
		mapplus$turn(false, ci);
	}

	@Unique
	private void mapplus$turn(boolean forward, CallbackInfo ci) {
		int target = mapplus$turnTarget(forward);
		if (target == -2) {
			return;
		}
		if (target >= 0) {
			setPage(target);
		}
		ci.cancel();
	}

	@Inject(method = "updateButtonVisibility", at = @At("TAIL"))
	private void mapplus$buttonsSkipMaps(CallbackInfo ci) {
		if (mapplus$atlas() != null) {
			forwardButton.visible = mapplus$turnTarget(true) >= 0;
			backButton.visible = mapplus$turnTarget(false) >= 0;
		}
	}

	@Override
	public InteractionHand mapplus$atlasHand() {
		if ((Object) this instanceof LecternScreen || minecraft == null || minecraft.player == null) {
			return null;
		}
		ItemStack source = AtlasSpread.source(bookAccess);
		if (source.isEmpty() || mapplus$atlas() == null) {
			return null;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			if (minecraft.player.getItemInHand(hand) == source) {
				return hand;
			}
		}
		return null;
	}

	@Override
	public int mapplus$atlasPage() {
		return currentPage;
	}
}
