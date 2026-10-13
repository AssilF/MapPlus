package com.assilf.mapplus.ink;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * The tools of the drawing desk. A tool is an item the player carries on the
 * cursor over the map: what it is decides how the map is drawn on, while the
 * item in the table's second slot decides with what.
 */
public enum InkTool {
	PEN("pen", stack -> stack.is(Items.FEATHER), new ItemStack(Items.FEATHER), 1, false),
	BRUSH("brush", stack -> stack.is(Items.BRUSH), new ItemStack(Items.BRUSH), 3, false),
	LINE("line", stack -> stack.is(Items.STRING), new ItemStack(Items.STRING), 1, false),
	BOX("box", stack -> stack.is(Items.ITEM_FRAME), new ItemStack(Items.ITEM_FRAME), 1, false),
	FILL("fill", stack -> stack.is(Items.BUCKET), new ItemStack(Items.BUCKET), 1, false),
	BORDER("border", stack -> stack.is(Items.IRON_CHAIN), new ItemStack(Items.IRON_CHAIN), 1, true),
	LABEL("label", stack -> stack.is(ItemTags.SIGNS) || stack.is(ItemTags.HANGING_SIGNS), new ItemStack(Items.OAK_SIGN), 1, true),
	PIN("pin", stack -> stack.is(ItemTags.BANNERS), new ItemStack(Items.WHITE_BANNER), 1, true);

	private final String name;
	private final Predicate<ItemStack> item;
	private final ItemStack icon;
	private final int size;
	private final boolean marking;

	InkTool(String name, Predicate<ItemStack> item, ItemStack icon, int size, boolean marking) {
		this.name = name;
		this.item = item;
		this.icon = icon;
		this.size = size;
		this.marking = marking;
	}

	/** The tool an item is, or null if it isn't one. */
	public static InkTool of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		for (InkTool tool : values()) {
			if (tool.item.test(stack)) {
				return tool;
			}
		}
		return null;
	}

	public boolean matches(ItemStack stack) {
		return !stack.isEmpty() && item.test(stack);
	}

	/** The item shown for this tool when the player has none to show. */
	public ItemStack icon() {
		return icon;
	}

	/** How wide a pen draws; other tools draw one pixel wide. */
	public int size() {
		return size;
	}

	/** Whether the tool marks the map up for planning, which needs a loom next to the table. */
	public boolean marking() {
		return marking;
	}

	/** Whether the tool lays ink on the map's pixels, as opposed to placing labels and pins. */
	public boolean draws() {
		return this != LABEL && this != PIN;
	}

	public Component title() {
		return Component.translatable("mapplus.desk.tool." + name);
	}

	public Component description() {
		return Component.translatable("mapplus.desk.tool." + name + ".description");
	}
}
