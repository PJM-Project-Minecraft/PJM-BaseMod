package ru.liko.pjmbasemod.common.radiospawn;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.RadioSpawnListPacket;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.teams.Teams;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Спавн на радейках (как Radio Backpack в Arma Reforger): живой сокомандник
 * с надетым на спину {@code warbornrenewed:backpack-ussr-radio} (curio-слот
 * "back") — мобильная точка возрождения фракции.
 *
 * <p>Активны только {@value #MAX_ACTIVE} рации на фракцию — слоты занимают
 * онлайн-носители в порядке появления (надел рацию / зашёл на сервер). Вышел —
 * слот освобождается и переходит следующему; вернувшийся при занятых слотах
 * видит в actionbar «рация недоступна». Раз в секунду {@link #onServerTick}
 * пересчитывает слоты.</p>
 *
 * <p>При смерти игроку уходит {@link RadioSpawnListPacket} со списком активных
 * носителей его фракции; выбор с экрана смерти приходит {@code RadioSpawnSelectPacket}
 * и применяется телепортом к текущей позиции носителя в {@code PlayerRespawnEvent}.
 * Носитель получает {@value #CARRIER_XP} XP за каждого возродившегося.</p>
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class RadioSpawnManager {

    /** Максимум активных раций (точек возрождения) на фракцию. */
    public static final int MAX_ACTIVE = 3;
    /** XP носителю за каждого возродившегося на его рации. */
    public static final int CARRIER_XP = 50;
    /** Перезарядка рации после возрождения на ней, секунды: не даёт вывалить на неё всё отделение разом. */
    public static final int SPAWN_COOLDOWN_SECONDS = 20;

    private static final ResourceLocation RADIO_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath("warbornrenewed", "backpack-ussr-radio");

    /** Слоты раций: фракция → UUID онлайн-носителей в порядке занятия. Активны первые {@link #MAX_ACTIVE}. */
    private static final Map<String, List<UUID>> CLAIMS = new HashMap<>();

    /** Выбор точки возрождения: погибший → UUID носителя рации. Живёт до респавна. */
    private static final Map<UUID, UUID> PENDING = new ConcurrentHashMap<>();

    /** Рации на перезарядке: носитель → игровой тик, начиная с которого рация снова доступна. */
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private static int tickCounter;

    private RadioSpawnManager() {}

    /** Носит ли игрок радио-рюкзак в curio-слоте (Curios обязателен для Warborn, но подстрахуемся). */
    private static boolean isWearingRadio(ServerPlayer player) {
        if (!ModList.get().isLoaded("curios")) return false;
        return CuriosHolder.check(player);
    }

    /** Отдельный класс, чтобы classloader не трогал Curios API без установленного мода. */
    private static final class CuriosHolder {
        static boolean check(ServerPlayer player) {
            return CuriosApi.getCuriosInventory(player)
                    .map(inv -> inv.findFirstCurio(stack ->
                            RADIO_ITEM_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))).isPresent())
                    .orElse(false);
        }
    }

    // ---------------------------------------------------------------- слоты раций

    /** Раз в секунду: актуализирует слоты и шлёт actionbar вытесненным носителям. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % 20 != 0) return;
        refresh(event.getServer());
    }

    private static void refresh(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        COOLDOWNS.values().removeIf(readyAt -> readyAt <= now);

        // Текущие носители по фракциям. Смерть слота не отнимает — иначе каждая
        // смерть носителя перетасовывала бы порядок раций.
        Map<String, List<UUID>> wearers = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String team = Teams.resolvePlayerTeamId(p);
            if (team == null || team.isBlank() || p.isSpectator()) continue;
            if (!isWearingRadio(p)) continue;
            wearers.computeIfAbsent(team, k -> new ArrayList<>()).add(p.getUUID());
        }

        Set<String> teams = new HashSet<>(CLAIMS.keySet());
        teams.addAll(wearers.keySet());
        for (String team : teams) {
            List<UUID> current = wearers.getOrDefault(team, List.of());
            List<UUID> claims = CLAIMS.computeIfAbsent(team, k -> new ArrayList<>());
            claims.retainAll(current);                       // вышел / снял рацию / сменил фракцию
            for (UUID id : current) {
                if (!claims.contains(id)) claims.add(id);    // новый носитель — в конец очереди
            }
            if (claims.isEmpty()) {
                CLAIMS.remove(team);
                continue;
            }
            // Вытесненные (за пределами лимита) — статус в actionbar.
            for (int i = MAX_ACTIVE; i < claims.size(); i++) {
                ServerPlayer p = server.getPlayerList().getPlayer(claims.get(i));
                if (p != null) {
                    p.displayClientMessage(Component.literal(
                            "Ваша рация недоступна: у фракции уже " + MAX_ACTIVE + " активных.")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
        }
    }

    private static boolean isActiveCarrier(String team, UUID playerId) {
        List<UUID> claims = CLAIMS.get(team);
        if (claims == null) return false;
        int index = claims.indexOf(playerId);
        return index >= 0 && index < MAX_ACTIVE;
    }

    /** Живые активные носители рации фракции, кроме самого погибшего. */
    private static List<ServerPlayer> activeCarriers(ServerPlayer victim, String team) {
        List<ServerPlayer> result = new ArrayList<>();
        List<UUID> claims = CLAIMS.getOrDefault(team, List.of());
        for (int i = 0; i < claims.size() && i < MAX_ACTIVE; i++) {
            ServerPlayer p = victim.server.getPlayerList().getPlayer(claims.get(i));
            if (p == null || p == victim || p.isDeadOrDying() || p.isSpectator()) continue;
            result.add(p);
        }
        return result;
    }

    // ---------------------------------------------------------------- возрождение

    /** Секунд до готовности рации носителя; 0 — доступна. */
    private static int cooldownSeconds(MinecraftServer server, UUID carrierId) {
        Long readyAt = COOLDOWNS.get(carrierId);
        if (readyAt == null) return 0;
        long left = readyAt - server.overworld().getGameTime();
        return left <= 0 ? 0 : (int) Math.ceil(left / 20.0);
    }

    /**
     * Вызывается из {@code PjmServerEvents.onLivingDeath}: варианты возрождения на экран смерти.
     * Рация на перезарядке остаётся в списке с остатком времени — клиент досчитывает его локально
     * и разблокирует кнопку сам, иначе игрок не понимал бы, почему точка пропала.
     */
    public static void sendDeathOptions(ServerPlayer victim) {
        List<RadioSpawnListPacket.Entry> entries = new ArrayList<>();
        String team = Teams.resolvePlayerTeamId(victim);
        if (team != null && !team.isBlank()) {
            for (ServerPlayer carrier : activeCarriers(victim, team)) {
                entries.add(new RadioSpawnListPacket.Entry(carrier.getUUID(),
                        carrier.getGameProfile().getName(), carrier.blockPosition(),
                        cooldownSeconds(victim.server, carrier.getUUID())));
            }
        }
        PjmNetworking.sendToPlayer(victim, new RadioSpawnListPacket(entries));
    }

    /** Обработчик {@code RadioSpawnSelectPacket}: запоминает выбор до респавна. */
    public static void selectSpawn(ServerPlayer player, UUID carrierId) {
        PENDING.put(player.getUUID(), carrierId);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID carrierId = PENDING.remove(player.getUUID());
        if (carrierId == null) return;

        ServerPlayer carrier = player.server.getPlayerList().getPlayer(carrierId);
        String team = Teams.resolvePlayerTeamId(player);
        if (carrier == null || carrier.isDeadOrDying() || carrier.isSpectator()
                || team == null || !team.equals(Teams.resolvePlayerTeamId(carrier))
                || !isWearingRadio(carrier) || !isActiveCarrier(team, carrierId)) {
            player.sendSystemMessage(Component.literal(
                    "Носитель рации недоступен — возрождение на базе.").withStyle(ChatFormatting.RED));
            return;
        }
        // Авторитетная проверка перезарядки: клиент разблокирует кнопку по своему таймеру,
        // но решает сервер (рация могла уйти на кулдаун уже после отправки списка).
        int cooldown = cooldownSeconds(player.server, carrierId);
        if (cooldown > 0) {
            player.sendSystemMessage(Component.literal(
                    "Рация перезаряжается (" + cooldown + " с) — возрождение на базе.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.teleportTo(carrier.serverLevel(), carrier.getX(), carrier.getY(), carrier.getZ(),
                carrier.getYRot(), carrier.getXRot());
        COOLDOWNS.put(carrierId, player.server.overworld().getGameTime() + SPAWN_COOLDOWN_SECONDS * 20L);
        RankService.addXp(carrier, CARRIER_XP, "radio_spawn");
        carrier.sendSystemMessage(Component.literal(player.getGameProfile().getName()
                + " возродился на вашей рации (+" + CARRIER_XP + " XP).").withStyle(ChatFormatting.YELLOW));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        // Слот освободится на ближайшем refresh(); удалять здесь нечего —
        // CLAIMS чистится по фактическому составу онлайна.
    }
}
