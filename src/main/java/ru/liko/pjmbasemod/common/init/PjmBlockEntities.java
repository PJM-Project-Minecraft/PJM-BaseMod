package ru.liko.pjmbasemod.common.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.blockentity.RemkaBlockEntity;

public final class PjmBlockEntities {

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Pjmbasemod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RemkaBlockEntity>> REMKA =
            BLOCK_ENTITIES.register("remka", () -> BlockEntityType.Builder
                    .of(RemkaBlockEntity::new, PjmBlocks.REMKA.get())
                    .build(null));

    private PjmBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
