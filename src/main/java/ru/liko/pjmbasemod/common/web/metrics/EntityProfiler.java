package ru.liko.pjmbasemod.common.web.metrics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.web.WebState;

/**
 * Профайлер entity: замер System.nanoTime() вокруг тика каждого entity через
 * EntityTickEvent.Pre/Post. Выключен по умолчанию, включается тумблером из панели.
 * В выключенном состоянии — одна проверка boolean на событие. Окно 30 секунд,
 * отчёт публикуется в {@link WebState}. Entity тикают на server thread —
 * пара startNanos/startId без синхронизации корректна.
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class EntityProfiler {

    private static final int FLUSH_EVERY_TICKS = 600; // 30 секунд
    private static final int TOP_LIMIT = 100;

    private static final ProfilerWindow WINDOW = new ProfilerWindow();
    private static volatile boolean active;
    private static long startNanos;
    // Один слот замера: при вложенном тике entity (пассажиры некоторых модов) внешний сэмпл отбрасывается — приемлемо для диагностики.
    private static int startedEntityId = Integer.MIN_VALUE;
    private static int ticksSinceFlush;

    private EntityProfiler() {}

    public static boolean isActive() {
        return active;
    }

    /** Включает/выключает профайлер. Включение блокируется конфигом web.profilerEnabled. */
    public static void setActive(boolean value) {
        if (value && !Config.isWebProfilerAllowed()) return;
        active = value;
        if (!value) {
            ticksSinceFlush = 0; // сброс, чтобы первый flush после ре-включения покрывал полное окно
            WINDOW.flush(0, 0); // возвращаемое значение не нужно — только очистка буфера
            WebState.setProfilerReport(ProfilerWindow.Report.empty());
            Pjmbasemod.LOGGER.info("[WebPanel] профайлер entity выключен");
        } else {
            Pjmbasemod.LOGGER.info("[WebPanel] профайлер entity включён");
        }
    }

    @SubscribeEvent
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!active) return;
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        startNanos = System.nanoTime();
        startedEntityId = entity.getId();
    }

    @SubscribeEvent
    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!active) return;
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || entity.getId() != startedEntityId) return;
        long nanos = System.nanoTime() - startNanos;
        startedEntityId = Integer.MIN_VALUE;
        WINDOW.record(entity.getUUID().toString(),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity.getName().getString(),
                entity.level().dimension().location().toString(),
                entity.getX(), entity.getY(), entity.getZ(), nanos);
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (!active) return;
        if (++ticksSinceFlush < FLUSH_EVERY_TICKS) return;
        ticksSinceFlush = 0;
        WebState.setProfilerReport(WINDOW.flush(FLUSH_EVERY_TICKS * 50L, TOP_LIMIT));
    }
}
