package com.assilf.mapplus.client.desk;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The ways of drawing on the canvas, shown as buttons under it. */
public enum DeskTool {
	PEN("pen", Items.FEATHER),
	LINE("line", Items.STRING),
	BOX("box", Items.ITEM_FRAME),
	FILL("fill", Items.BUCKET);

	private final String name;
	private final ItemStack icon;

	DeskTool(String name, Item icon) {
		this.name = name;
		this.icon = new ItemStack(icon);
	}

	public ItemStack icon() {
		return icon;
	}

	public Component title() {
		return Component.translatable("mapplus.desk.tool." + name);
	}

	public Component description() {
		return Component.translatable("mapplus.desk.tool." + name + ".description");
	}
}
