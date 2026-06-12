package ru.liko.pjmbasemod.client.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.liko.pjmbasemod.common.compat.TaczWarehouseCompat;

/** Утилита для получения {@link ItemStack}-иконки по строковому id предмета. */
public final class GuiItemIcons {

    private GuiItemIcons() {}

    public static ItemStack stackFor(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            ItemStack taczStack = id == null ? ItemStack.EMPTY : TaczWarehouseCompat.createStack(id, 1);
            return taczStack.isEmpty() ? new ItemStack(Items.MINECART) : taczStack;
        }
        return new ItemStack(item);
    }
}
