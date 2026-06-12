package ru.liko.pjmbasemod.common.garage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * Одна позиция стоимости техники: предмет + количество.
 * {@code item} хранится строкой-идентификатором (например "minecraft:iron_ingot").
 */
public record CostEntry(String item, int count) {

    public CostEntry {
        if (count < 1) count = 1;
    }

    @Nullable
    public Item resolveItem() {
        ResourceLocation id = ResourceLocation.tryParse(item);
        if (id == null) return null;
        Item resolved = BuiltInRegistries.ITEM.get(id);
        return resolved == Items.AIR ? null : resolved;
    }

    public ItemStack toDisplayStack() {
        Item resolved = resolveItem();
        return resolved == null ? ItemStack.EMPTY : new ItemStack(resolved, count);
    }
}
