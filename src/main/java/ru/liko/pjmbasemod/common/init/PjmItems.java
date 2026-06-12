package ru.liko.pjmbasemod.common.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.garage.GarageType;
import ru.liko.pjmbasemod.common.item.NotebookItem;
import ru.liko.pjmbasemod.common.item.SupplyCrateItem;

public final class PjmItems {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Pjmbasemod.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Pjmbasemod.MODID);

    // Ноутбук-терминал наземного гаража.
    public static final DeferredHolder<Item, Item> NOTEBOOK =
            ITEMS.register("notebook", () -> new NotebookItem(new Item.Properties().stacksTo(16), GarageType.GROUND));
    // Ноутбук-терминал авиационного гаража.
    public static final DeferredHolder<Item, Item> NOTEBOOK_AIR =
            ITEMS.register("notebook_air", () -> new NotebookItem(new Item.Properties().stacksTo(16), GarageType.AVIATION));

    // Ящики поставок склада. id предмета = crateId в CrateRegistry.
    public static final DeferredHolder<Item, Item> WEAPON_CRATE = registerCrate("weapon_crate");
    public static final DeferredHolder<Item, Item> SUPPLY_CRATE = registerCrate("supply_crate");
    public static final DeferredHolder<Item, Item> EQUIPMENT_CRATE = registerCrate("equipment_crate");
    public static final DeferredHolder<Item, Item> RAW_CRATE = registerCrate("raw_crate");
    public static final DeferredHolder<Item, Item> SPECIAL_CRATE = registerCrate("special_crate");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.pjmbasemod"))
                    .icon(() -> NOTEBOOK.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(NOTEBOOK.get());
                        output.accept(NOTEBOOK_AIR.get());
                    })
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WAREHOUSE_TAB =
            TABS.register("warehouse", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.pjmbasemod.warehouse"))
                    .withTabsBefore(TAB.getId())
                    .icon(() -> WEAPON_CRATE.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(WEAPON_CRATE.get());
                        output.accept(SUPPLY_CRATE.get());
                        output.accept(EQUIPMENT_CRATE.get());
                        output.accept(RAW_CRATE.get());
                        output.accept(SPECIAL_CRATE.get());
                    })
                    .build());

    private static DeferredHolder<Item, Item> registerCrate(String crateId) {
        return ITEMS.register(crateId, () -> new SupplyCrateItem(new Item.Properties().stacksTo(16), crateId));
    }

    private PjmItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
