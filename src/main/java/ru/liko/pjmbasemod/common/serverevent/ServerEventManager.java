package ru.liko.pjmbasemod.common.serverevent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.EventMapSyncPacket;
import ru.liko.pjmbasemod.common.network.packet.NotificationPacket;
import ru.liko.pjmbasemod.common.rank.RankService;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Система серверных событий: планировщик автозапуска
 * + жизненный цикл активного события. Тик — раз в секунду из PjmServerEvents.onServerTick.
 *
 * <p>Типы событий регистрируются через {@link #registerType(ServerEventType)} и
 * диспетчеризируются по {@link ServerEvent#typeId()}. Менеджер агностичен к конкретным
 * классам событий.</p>
 */
public final class ServerEventManager {

    private static final int TICK_INTERVAL = 20;

    /** Зарегистрированные типы событий по typeId. Порядок сохранён для автозапуска. */
    private static final Map<String, ServerEventType> TYPES = new LinkedHashMap<>();

    private static final int WARN_COLOR = 0xFFAA22;

    @Nullable
    private static ServerEvent activeEvent;
    private static int tickCounter;

    // Отложенный старт: событие уже создано (точка выбрана) и ждёт обратного отсчёта.
    // In-memory: при рестарте отсчёт сбрасывается, планировщик взведёт новое.
    @Nullable
    private static ServerEvent pendingEvent;
    private static long pendingStartAtSeconds;
    /** Порог напоминания перед стартом (сек). Отсчёт длиннее — игрок получит второе предупреждение. */
    private static final int REMINDER_SECONDS = 60;
    private static boolean reminderSent;

    private ServerEventManager() {}

    /** Регистрация типа события. Должна вызываться на ранней загрузке мода. */
    public static synchronized void registerType(ServerEventType type) {
        if (type == null) return;
        TYPES.put(type.typeId(), type);
    }

    @Nullable
    public static ServerEventType type(@Nullable String typeId) {
        if (typeId == null) return null;
        return TYPES.get(typeId);
    }

    /** Все доступные типы (available=true) — для автозапуска и команд. */
    public static List<ServerEventType> availableTypes(MinecraftServer server) {
        List<ServerEventType> list = new ArrayList<>();
        for (ServerEventType type : TYPES.values()) {
            if (type.available(server)) {
                list.add(type);
            }
        }
        return list;
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null) return;
        tickCounter++;
        if (tickCounter % TICK_INTERVAL != 0) return;

        if (activeEvent != null) {
            activeEvent.tick(server);
            if (activeEvent.isFinished()) {
                finishEvent(server, false);
            } else {
                persistActiveEvent(server);
            }
            return;
        }

        if (pendingEvent != null) {
            tickPending(server);
            return;
        }

        tickScheduler(server);
    }

    /** Обратный отсчёт до старта отложенного события. По истечении — запускает его. */
    private static synchronized void tickPending(MinecraftServer server) {
        long left = pendingStartAtSeconds - Instant.now().getEpochSecond();
        if (left > 0) {
            if (left <= REMINDER_SECONDS && !reminderSent) {
                reminderSent = true;
                warnPending(server, pendingEvent, (int) left);
            }
            return;
        }
        ServerEvent event = pendingEvent;
        clearPending();
        launch(server, event);
    }

    private static void tickScheduler(MinecraftServer server) {
        if (!Config.isEventsEnabled()) return;
        if (server.getPlayerList().getPlayerCount() == 0) return;
        // Пауза автозапуска, пока идёт захват фронтлайна (если так настроено).
        if (Config.isEventsRequireCaptureInactive() && Config.isCapturePointsEnabled()) return;

        ServerEventSavedData data = ServerEventSavedData.get(server);
        long now = Instant.now().getEpochSecond();
        if (data.nextEventAtEpochSeconds() == 0) {
            scheduleNext(data, now);
            return;
        }
        if (now < data.nextEventAtEpochSeconds()) return;

        // Шанс запуска: событие стартует не всегда — иначе просто перепланируем таймер.
        double chance = Config.getEventsSpawnChance();
        if (chance < 1.0 && ThreadLocalRandom.current().nextDouble() >= chance) {
            scheduleNext(data, now);
            return;
        }

        String error = startAutoEvent(server);
        if (error != null) {
            // Нечего запускать (нет точек/мода) — переносим попытку, чтобы не молотить каждую секунду.
            Pjmbasemod.LOGGER.warn("Events: автозапуск события не удался: {}", error);
            scheduleNext(data, now);
        }
    }

    private static void scheduleNext(ServerEventSavedData data, long now) {
        int min = Config.getEventsMinIntervalMinutes();
        int max = Config.getEventsMaxIntervalMinutes();
        long delayMinutes = min >= max ? min : ThreadLocalRandom.current().nextLong(min, max + 1L);
        data.setNextEventAtEpochSeconds(now + delayMinutes * 60);
        Pjmbasemod.LOGGER.info("Events: следующее событие через {} мин.", delayMinutes);
    }

    /**
     * Автозапуск: выбирает случайный тип (по весам) и либо стартует сразу, либо взводит
     * обратный отсчёт {@code events.startDelaySeconds} с предупреждением игрокам.
     *
     * @return null при успехе, иначе текст ошибки
     */
    @Nullable
    private static synchronized String startAutoEvent(MinecraftServer server) {
        ServerEventType type = pickWeightedType(server);
        if (type == null) {
            return "нет доступных типов событий";
        }
        ServerEventType.CreateResult result = type.create(server, null);
        if (result.event() == null) {
            return result.error() != null ? result.error() : "не удалось создать событие";
        }
        int delay = Config.getEventsStartDelaySeconds();
        if (delay <= 0) {
            launch(server, result.event());
        } else {
            armPending(server, result.event(), delay);
        }
        return null;
    }

    /**
     * Взводит обратный отсчёт до старта уже созданного события: предупреждает игроков
     * (с указанием района) и отдаёт его зону на карту — район виден заранее.
     */
    private static void armPending(MinecraftServer server, ServerEvent event, int delaySeconds) {
        pendingEvent = event;
        pendingStartAtSeconds = Instant.now().getEpochSecond() + delaySeconds;
        reminderSent = delaySeconds <= REMINDER_SECONDS; // отсчёт короче порога — напоминание не нужно
        // Планировщик не должен взводить второе событие, пока идёт отсчёт.
        ServerEventSavedData.get(server).setNextEventAtEpochSeconds(0);
        warnPending(server, event, delaySeconds);
        broadcastZone(server);
        Pjmbasemod.LOGGER.info("Events: событие '{}' начнётся через {} сек. — {}.",
                event.typeId(), delaySeconds, event.statusLine());
    }

    /** Предупреждение о скором событии: тип, район и время до старта. */
    private static void warnPending(MinecraftServer server, ServerEvent event, int secondsLeft) {
        ServerEvent.EventZone zone = event.zone();
        String point = zone != null ? zone.pointName() : "";
        Component what = Component.translatable("event.pjmbasemod." + event.typeId() + ".zone", point);
        Component time = secondsLeft >= 60
                ? Component.translatable("event.pjmbasemod.warning.minutes", secondsLeft / 60)
                : Component.translatable("event.pjmbasemod.warning.seconds", secondsLeft);
        PjmNetworking.sendToAll(server, new NotificationPacket(
                Component.translatable("event.pjmbasemod.warning.title"),
                Component.translatable("event.pjmbasemod.warning.subtitle", what, time),
                WARN_COLOR, 8000L));
    }

    private static void clearPending() {
        pendingEvent = null;
        pendingStartAtSeconds = 0;
        reminderSent = false;
    }

    /** Общий путь запуска созданного события: активация, персист, зона на карту. */
    private static void launch(MinecraftServer server, ServerEvent event) {
        // Следующий автозапуск планируется уже после завершения этого события.
        ServerEventSavedData.get(server).setNextEventAtEpochSeconds(0);
        activeEvent = event;
        event.onStart(server);
        persistActiveEvent(server);
        broadcastZone(server);
    }

    /**
     * Случайный тип с учётом весов из конфига ({@code events.typeWeights}): вес {@code <= 0}
     * отключает тип, отсутствующий в карте тип считается весом 1.0. {@code null} — выбирать нечего.
     */
    @Nullable
    private static ServerEventType pickWeightedType(MinecraftServer server) {
        List<ServerEventType> types = availableTypes(server);
        Map<String, Double> weights = Config.getEventTypeWeights();
        List<ServerEventType> pool = new ArrayList<>();
        double total = 0;
        for (ServerEventType type : types) {
            double weight = weights.getOrDefault(type.typeId(), 1.0);
            if (weight <= 0) continue; // тип отключён в конфиге
            total += weight;
            pool.add(type);
        }
        if (pool.isEmpty()) return null;
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0;
        for (ServerEventType type : pool) {
            cursor += weights.getOrDefault(type.typeId(), 1.0);
            if (roll < cursor) return type;
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * Запускает событие указанного типа. {@code typeId} = null — автозапуск случайного типа.
     * {@code pointName} — конкретная точка/зона или null (случайная).
     *
     * @return null при успехе, иначе текст ошибки для команды.
     */
    @Nullable
    public static synchronized String startEvent(MinecraftServer server, @Nullable String typeId, @Nullable String pointName) {
        if (activeEvent != null) {
            return "событие уже идёт: " + activeEvent.statusLine();
        }
        clearPending(); // ручной запуск отменяет обратный отсчёт (если был взведён)
        ServerEventType type;
        if (typeId == null || typeId.isBlank()) {
            type = pickWeightedType(server);
            if (type == null) {
                return "нет доступных типов событий";
            }
        } else {
            type = TYPES.get(typeId);
            if (type == null) {
                return "неизвестный тип события: " + typeId;
            }
            if (!type.available(server)) {
                return "тип события '" + typeId + "' недоступен в текущем окружении";
            }
        }

        ServerEventType.CreateResult result = type.create(server, pointName);
        if (result.event() == null) {
            return result.error() != null ? result.error() : "не удалось создать событие";
        }
        launch(server, result.event());
        return null;
    }

    /** Останавливает активное событие вручную. @return false — активного события нет. */
    public static synchronized boolean stopEvent(MinecraftServer server) {
        if (activeEvent == null) {
            if (pendingEvent != null) {
                clearPending();
                PjmNetworking.sendToAll(server, EventMapSyncPacket.inactive()); // убрать анонсированный район с карты
                scheduleNext(ServerEventSavedData.get(server), Instant.now().getEpochSecond());
                return true;
            }
            return false;
        }
        finishEvent(server, true);
        return true;
    }

    private static void finishEvent(MinecraftServer server, boolean aborted) {
        ServerEvent event = activeEvent;
        activeEvent = null;
        if (event != null) {
            event.onStop(server, aborted);
        }
        ServerEventSavedData.get(server).setActiveEvent(null);
        PjmNetworking.sendToAll(server, EventMapSyncPacket.inactive());
    }

    /** Начисляет XP игроку, сбившему событийный дрон. Вызывается из WrbDronesIntegration. */
    public static void onEventDroneShotDown(ServerPlayer player, UUID droneId) {
        ServerEvent event = activeEvent;
        if (!(event instanceof DroneRaidEvent raid)) return;
        if (raid.onDroneShotDown(droneId) && raid.xpPerKill() > 0) {
            RankService.addXp(player, raid.xpPerKill(), "event_drone");
        }
    }

    /** Засчитывает попадание событийного дрона в цель. Вызывается из WrbDronesIntegration. */
    public static void onEventDroneImpacted(UUID droneId) {
        ServerEvent event = activeEvent;
        if (event instanceof DroneRaidEvent raid) {
            raid.onDroneImpacted(droneId);
        }
    }

    /** Восстановление активного события после рестарта сервера. */
    public static void onServerStarted(MinecraftServer server) {
        ServerEventSavedData data = ServerEventSavedData.get(server);
        CompoundTag tag = data.activeEvent();
        if (tag == null) return;

        String type = tag.getString("Type");
        ServerEventType factory = TYPES.get(type);
        if (factory == null) {
            Pjmbasemod.LOGGER.warn("Events: неизвестный тип сохранённого события '{}' — сброшен.", type);
            data.setActiveEvent(null);
            return;
        }
        if (!factory.available(server)) {
            Pjmbasemod.LOGGER.warn("Events: сохранённое событие '{}' не восстановлено — тип недоступен.", type);
            data.setActiveEvent(null);
            return;
        }
        try {
            activeEvent = factory.load(tag);
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Events: ошибка восстановления события '{}'.", type, e);
            data.setActiveEvent(null);
            return;
        }
        Pjmbasemod.LOGGER.info("Events: восстановлено событие — {}.", activeEvent.statusLine());
        broadcastZone(server);
    }

    /** Первичная синхронизация зоны события подключившемуся игроку. */
    public static void sendInitialSync(ServerPlayer player) {
        EventMapSyncPacket packet = currentZonePacket();
        if (packet.active()) {
            PjmNetworking.sendToPlayer(player, packet);
        }
    }

    @Nullable
    public static ServerEvent activeEvent() {
        return activeEvent;
    }

    public static String status() {
        if (activeEvent != null) {
            return "активно: " + activeEvent.statusLine();
        }
        if (pendingEvent != null) {
            long left = Math.max(0, pendingStartAtSeconds - Instant.now().getEpochSecond());
            return "обратный отсчёт: " + pendingEvent.statusLine() + " — старт через " + left + " сек";
        }
        return "событий нет";
    }

    private static void persistActiveEvent(MinecraftServer server) {
        if (activeEvent == null) return;
        CompoundTag tag = activeEvent.save();
        tag.putString("Type", activeEvent.typeId());
        ServerEventSavedData.get(server).setActiveEvent(tag);
    }

    private static void broadcastZone(MinecraftServer server) {
        PjmNetworking.sendToAll(server, currentZonePacket());
    }

    private static EventMapSyncPacket currentZonePacket() {
        // Район анонсированного события показываем на карте уже во время обратного отсчёта.
        ServerEvent event = activeEvent != null ? activeEvent : pendingEvent;
        ServerEvent.EventZone zone = event != null ? event.zone() : null;
        if (zone == null) {
            return EventMapSyncPacket.inactive();
        }
        return new EventMapSyncPacket(true, event.typeId(), zone.pointName(), zone.dimension(),
                zone.centerX(), zone.centerY(), zone.centerZ(), zone.radius());
    }
}
