package com.assilf.mapplus.client.mixin;

import com.assilf.mapplus.atlas.Atlas;
import com.assilf.mapplus.client.atlas.AtlasBookScreen;
import com.assilf.mapplus.client.atlas.AtlasSpread;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * An atlas in the vanilla book and quill screen: written pages work as usual,
 * and a stitched map shows on the page facing its notes.
 */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends Screen implements AtlasBookScreen {
	@Shadow
	private int currentPage;

	@Shadow
	@Final
	private List<String> pages;

	@Shadow
	private MultiLineEditBox page;

	@Shadow
	private PageButton forwardButton;

	@Shadow
	private PageButton backButton;

	@Shadow
	@Final
	private InteractionHand hand;

	@Unique
	private Atlas mapplus$atlas;

	@Unique
	private int mapplus$editOffset;

	@Unique
	private int mapplus$backOffset;

	@Unique
	private int mapplus$forwardOffset;

	@Unique
	private int mapplus$turnedFrom;

	protected BookEditScreenMixin(Component title) {
		super(title);
	}

	@Shadow
	private int backgroundLeft() {
		throw new AssertionError();
	}

	@Shadow
	private void updatePageContent() {
		throw new AssertionError();
	}

	@Shadow
	private void updateButtonVisibility() {
		throw new AssertionError();
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mapplus$openAtlas(Player owner, ItemStack book, InteractionHand hand, WritableBookContent content, CallbackInfo ci) {
		mapplus$atlas = Atlas.read(book).orElse(null);
		if (mapplus$atlas == null) {
			return;
		}
		// Every map has the other page of its spread for its notes.
		while (pages.size() < mapplus$atlas.requiredPages()) {
			pages.add("");
		}
		currentPage = AtlasSpread.firstStop(mapplus$atlas, currentPage, pages.size());
	}

	@Unique
	private AtlasSpread.Layout mapplus$layout() {
		return AtlasSpread.layout(mapplus$atlas, currentPage, pages.size());
	}

	@ModifyReturnValue(method = "backgroundLeft", at = @At("RETURN"))
	private int mapplus$centerSpread(int left) {
		AtlasSpread.Layout layout = mapplus$layout();
		return layout == null ? left : left + layout.shift();
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void mapplus$measureWidgets(CallbackInfo ci) {
		if (mapplus$atlas == null) {
			return;
		}
		int left = backgroundLeft();
		// The text box is placed from the screen width, the page buttons from the page.
		mapplus$editOffset = page.getX() - (width - BookEditScreen.IMAGE_WIDTH) / 2;
		mapplus$backOffset = backButton.getX() - left;
		mapplus$forwardOffset = forwardButton.getX() - left;
		mapplus$followPage();
	}

	/** Keeps the page's widgets on the page as it moves between the left and right of a spread. */
	@Unique
	private void mapplus$followPage() {
		int left = backgroundLeft();
		page.setX(left + mapplus$editOffset);
		backButton.setX(left + mapplus$backOffset);
		forwardButton.setX(left + mapplus$forwardOffset);
		boolean text = !mapplus$atlas.isMapPage(currentPage);
		page.visible = text;
		page.active = text;
		if (!text && getFocused() == page) {
			setFocused(null);
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void mapplus$beforeExtract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (mapplus$atlas != null) {
			mapplus$followPage();
		}
	}

	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void mapplus$drawSpread(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y,
			float u, float v, int width, int height, int textureWidth, int textureHeight, Operation<Void> original) {
		AtlasSpread.Layout layout = mapplus$layout();
		if (layout == null) {
			original.call(graphics, pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
			return;
		}
		AtlasSpread.drawPage(graphics, texture, x, y, layout.mirrored());
		AtlasSpread.drawSpread(graphics, layout, mapplus$atlas, x, y, index -> Component.literal(pages.get(index)));
	}

	@Override
	public int mapplus$turnTarget(boolean forward) {
		if (mapplus$atlas == null) {
			return -2;
		}
		return AtlasSpread.turnTarget(mapplus$atlas, currentPage, pages.size(), forward);
	}

	@Inject(method = "pageForward", at = @At("HEAD"), cancellable = true)
	private void mapplus$turnForward(CallbackInfo ci) {
		int target = mapplus$turnTarget(true);
		if (target == -2) {
			return;
		}
		mapplus$turnedFrom = currentPage;
		if (target >= 0) {
			mapplus$turnTo(target);
			ci.cancel();
		} else {
			// Past the last spread: vanilla adds a new page after the last one.
			currentPage = pages.size() - 1;
		}
	}

	@Inject(method = "pageForward", at = @At("TAIL"))
	private void mapplus$afterTurnForward(CallbackInfo ci) {
		if (mapplus$atlas != null && !AtlasSpread.isStop(mapplus$atlas, currentPage, pages.size())) {
			// The book is full; stay where we were.
			mapplus$turnTo(mapplus$turnedFrom);
		}
	}

	@Inject(method = "pageBack", at = @At("HEAD"), cancellable = true)
	private void mapplus$turnBack(CallbackInfo ci) {
		int target = mapplus$turnTarget(false);
		if (target == -2) {
			return;
		}
		if (target >= 0) {
			mapplus$turnTo(target);
		}
		ci.cancel();
	}

	@Unique
	private void mapplus$turnTo(int target) {
		currentPage = target;
		updatePageContent();
		updateButtonVisibility();
		mapplus$followPage();
	}

	@Inject(method = "updateButtonVisibility", at = @At("TAIL"))
	private void mapplus$backButtonOnFirstSpread(CallbackInfo ci) {
		if (mapplus$atlas != null) {
			backButton.visible = mapplus$turnTarget(false) >= 0;
		}
	}

	/** Trailing empty pages are dropped on save, except the notes pages facing maps. */
	@Inject(method = "eraseEmptyTrailingPages", at = @At("HEAD"), cancellable = true)
	private void mapplus$keepNotesPages(CallbackInfo ci) {
		if (mapplus$atlas == null) {
			return;
		}
		ci.cancel();
		int keep = mapplus$atlas.requiredPages();
		while (pages.size() > keep && pages.getLast().isEmpty()) {
			pages.removeLast();
		}
	}

	@Override
	public InteractionHand mapplus$atlasHand() {
		return mapplus$atlas == null ? null : hand;
	}

	@Override
	public int mapplus$atlasPage() {
		return currentPage;
	}
}
