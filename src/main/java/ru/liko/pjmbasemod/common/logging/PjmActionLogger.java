package ru.liko.pjmbasemod.common.logging;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.loading.FMLPaths;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Читаемый лог действий игроков в отдельную папку {@code <game>/pjmlogs/}.
 *
 * <p>Один файл на день ({@code pjmlogs/YYYY-MM-DD.log}, режим append), строки вида
 * {@code [HH:mm:ss] [TAG] сообщение на русском}. Логируются убийства (PvP), уничтожение
 * техники SuperbWarfare, вход/выход игроков и ключевые действия подсистем мода.</p>
 *
 * <p><b>Асинхронность.</b> Публичные методы вызываются из игрового потока и лишь кладут
 * готовую строку в очередь — I/O выполняет отдельный daemon-поток {@code pjm-action-logger}.
 * Игровой поток никогда не ждёт диск (тот же инвариант, что у веб-панели). Метка времени
 * снимается в момент вызова (в игровом потоке), поэтому порядок и время в логе точные.</p>
 *
 * <p>Гейт по конфигу: при {@code logging.enabled == false} все методы — no-op, поток
 * записи не стартует.</p>
 */
public final class PjmActionLogger {

    private static final PjmActionLogger INSTANCE = new PjmActionLogger();

    /** Маркер завершения для потока записи. */
    private static final Entry POISON = new Entry(null, null);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BlockingQueue<Entry> queue = new LinkedBlockingQueue<>();

    private volatile boolean running = false;
    private Thread worker;

    private PjmActionLogger() {}

    public static PjmActionLogger instance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ жизненный цикл

    /** Запускает поток записи. Вызывается на {@code ServerStartedEvent}. */
    public synchronized void start() {
        if (running) return;
        if (!Config.isLoggingEnabled()) return;
        queue.clear();
        running = true;
        worker = new Thread(this::runLoop, "pjm-action-logger");
        worker.setDaemon(true);
        worker.start();
        Pjmbasemod.LOGGER.info("PjmActionLogger: логирование действий включено ({}).", logDir());
    }

    /** Останавливает поток записи, дожидаясь слива очереди. Вызывается на {@code ServerStoppingEvent}. */
    public synchronized void stop() {
        if (!running) return;
        running = false;
        queue.offer(POISON);
        if (worker != null) {
            try {
                worker.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
    }

    // ------------------------------------------------------------------ публичный API (игровой поток)

    /** Убийство игрока игроком (PvP). {@code cause} — оружие/тип урона. */
    public void logKill(@Nullable ServerPlayer killer, ServerPlayer victim, String cause) {
        if (!running || killer == null || victim == null) return;
        BlockPos pos = victim.blockPosition();
        String teams = formatTeams(teamOf(killer), teamOf(victim));
        String causePart = cause == null || cause.isBlank() ? "" : " (" + cause + ")";
        enqueue(LogCategory.KILL, String.format("%s → %s%s @ %d,%d,%d%s",
                killer.getGameProfile().getName(), victim.getGameProfile().getName(), causePart,
                pos.getX(), pos.getY(), pos.getZ(), teams));
    }

    /** Уничтожение техники SuperbWarfare. {@code attacker} — кто нанёс смертельный урон (может быть null). */
    public void logVehicleDestroyed(@Nullable Entity attacker, Entity vehicle) {
        if (!running || vehicle == null) return;
        BlockPos pos = vehicle.blockPosition();
        enqueue(LogCategory.VEHICLE, String.format("%s уничтожил технику %s @ %d,%d,%d",
                actorName(attacker), typeName(vehicle), pos.getX(), pos.getY(), pos.getZ()));
    }

    /** Вход ({@code joined=true}) или выход игрока. */
    public void logSession(ServerPlayer player, boolean joined) {
        if (!running || player == null) return;
        String name = player.getGameProfile().getName();
        enqueue(joined ? LogCategory.JOIN : LogCategory.LEFT,
                joined ? name + " вошёл в игру" : name + " вышел из игры");
    }

    /** Произвольное действие подсистемы мода — текст сообщения формирует вызывающий код. */
    public void logSubsystem(LogCategory category, String message) {
        if (!running || category == null || message == null || message.isBlank()) return;
        enqueue(category, message);
    }

    // ------------------------------------------------------------------ форматирование

    private void enqueue(LogCategory category, String message) {
        LocalDateTime now = LocalDateTime.now();
        String line = String.format("[%s] [%s] %s", now.format(TIME), category.tag(), message);
        queue.offer(new Entry(now.toLocalDate(), line));
    }

    @Nullable
    private static String teamOf(ServerPlayer player) {
        return player == null ? null : FrontlineTeams.resolvePlayerTeamId(player);
    }

    private static String formatTeams(@Nullable String killerTeam, @Nullable String victimTeam) {
        if (killerTeam == null && victimTeam == null) return "";
        return String.format(" [%s→%s]",
                killerTeam == null ? "?" : killerTeam,
                victimTeam == null ? "?" : victimTeam);
    }

    private static String actorName(@Nullable Entity attacker) {
        if (attacker == null) return "неизвестно";
        if (attacker instanceof ServerPlayer player) return player.getGameProfile().getName();
        return typeName(attacker);
    }

    /** Читаемое имя типа сущности — путь registry-id (например {@code bmp2}). */
    private static String typeName(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id.getPath();
    }

    // ------------------------------------------------------------------ поток записи

    private void runLoop() {
        LocalDate openDate = null;
        BufferedWriter writer = null;
        try {
            while (true) {
                Entry entry = queue.take();
                if (entry == POISON) break;
                try {
                    if (writer == null || !entry.date.equals(openDate)) {
                        writer = closeAndReopen(writer, entry.date);
                        openDate = entry.date;
                    }
                    writer.write(entry.line);
                    writer.newLine();
                    // Флашим, когда очередь опустела, чтобы не терять записи при падении, но не на каждой строке.
                    if (queue.isEmpty()) writer.flush();
                } catch (IOException e) {
                    Pjmbasemod.LOGGER.error("PjmActionLogger: ошибка записи в pjmlogs, запись действий приостановлена.", e);
                    running = false;
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closeQuietly(writer);
        }
    }

    private BufferedWriter closeAndReopen(@Nullable BufferedWriter current, LocalDate date) throws IOException {
        closeQuietly(current);
        Path dir = logDir();
        Files.createDirectories(dir);
        Path file = dir.resolve(date.format(DATE) + ".log");
        return Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void closeQuietly(@Nullable BufferedWriter writer) {
        if (writer == null) return;
        try {
            writer.flush();
            writer.close();
        } catch (IOException ignored) {
            // выход — не критично
        }
    }

    private static Path logDir() {
        return FMLPaths.GAMEDIR.get().resolve("pjmlogs");
    }

    /** Одна строка лога с датой (для выбора файла дня). */
    private record Entry(LocalDate date, String line) {}
}
