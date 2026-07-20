package ru.liko.pjmbasemod.common.init;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.block.RemkaBlock;

public final class PjmBlocks {

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Pjmbasemod.MODID);

    /**
     * Ремонтная станция «Ремка». {@code noOcclusion} обязателен: блоки структуры невидимы,
     * без него они гасили бы соседние грани и вокруг станции чернел бы куб.
     */
    public static final DeferredBlock<RemkaBlock> REMKA =
            BLOCKS.register("remka", () -> new RemkaBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    private PjmBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
