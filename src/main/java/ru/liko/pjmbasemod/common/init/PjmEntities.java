package ru.liko.pjmbasemod.common.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.entity.NotebookEntity;
import ru.liko.pjmbasemod.common.entity.QuartermasterEntity;

public final class PjmEntities {

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Pjmbasemod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<NotebookEntity>> NOTEBOOK =
            ENTITIES.register("notebook", () -> EntityType.Builder.<NotebookEntity>of(NotebookEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.5F) // Ширина 1 блок, высота 1.5
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build("notebook"));

    public static final DeferredHolder<EntityType<?>, EntityType<QuartermasterEntity>> QUARTERMASTER =
            ENTITIES.register("quartermaster", () -> EntityType.Builder.<QuartermasterEntity>of(QuartermasterEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("quartermaster"));

    private PjmEntities() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(PjmEntities::onCreateAttributes);
    }

    private static void onCreateAttributes(EntityAttributeCreationEvent event) {
        event.put(QUARTERMASTER.get(), QuartermasterEntity.createAttributes().build());
    }
}
