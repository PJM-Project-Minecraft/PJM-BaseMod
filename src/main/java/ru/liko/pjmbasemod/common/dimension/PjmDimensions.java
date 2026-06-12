package ru.liko.pjmbasemod.common.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * Ключи кастомных дименшенов мода. Сами дименшены задаются датапаком
 * (data/pjmbasemod/dimension/*.json) и не требуют регистрации через DeferredRegister.
 */
public final class PjmDimensions {

    /** Пустой void-дименшен «лобби», куда попадает игрок до выбора фракции. */
    public static final ResourceKey<Level> LOBBY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "lobby"));

    private PjmDimensions() {
    }
}
