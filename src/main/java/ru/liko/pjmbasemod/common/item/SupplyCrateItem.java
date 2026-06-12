package ru.liko.pjmbasemod.common.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Предмет-ящик поставки. Засчитывается складом, когда брошен в зону приёма
 * либо когда игрок взаимодействует им с NPC-кладовщиком. Сам по себе предмет
 * не несёт логики — пул очков и количество берутся из {@code CrateRegistry}
 * по id ящика (path зарегистрированного предмета).
 */
public class SupplyCrateItem extends Item {

    /** id ящика = path в реестре (weapon_crate, supply_crate, equipment_crate, raw_crate, special_crate). */
    private final String crateId;

    public SupplyCrateItem(Properties properties, String crateId) {
        super(properties);
        this.crateId = crateId;
    }

    public String crateId() {
        return crateId;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.pjmbasemod.supply_crate.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
