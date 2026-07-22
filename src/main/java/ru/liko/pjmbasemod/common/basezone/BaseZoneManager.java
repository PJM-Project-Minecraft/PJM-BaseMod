package ru.liko.pjmbasemod.common.basezone;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.alliance.Alliances;
import ru.liko.pjmbasemod.common.teams.Teams;

import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.BaseZoneMapSyncPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Серверный enforcement зон базы: предупреждение врагу, отсчёт и смерть. */
public final class BaseZoneManager {

    /** Кастомный источник урона (см. data/pjmbasemod/damage_type/base_zone.json). */
    public static final ResourceKey<DamageType> BASE_ZONE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "base_zone"));

    /** Оставшиеся тики отсчёта до смерти по игрокам (рантайм, не персистится). */
    private static final Map<UUID, Integer> COUNTDOWN = new HashMap<>();

    private BaseZoneManager() {}

    public static void onPlayerTick(ServerPlayer player) {
        if (!Config.isBaseZoneEnabled()) { clear(player); return; }

        // OP-приоритет + админ-осмотр: creative/spectator игнорируют защиту.
        if (player.hasPermissions(2) || player.isCreative() || player.isSpectator()) {
            clear(player);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel level = player.serverLevel();
        String dimension = level.dimension().location().toString();

        BaseZone zone = BaseZoneSavedData.get(server).findZoneAt(dimension, player.blockPosition());
        if (zone == null) { clear(player); return; }

        String team = Teams.resolvePlayerTeamId(player);
        // Союзник ходит по базе союзника как по своей — отсчёта и смерти нет.
        if (Alliances.friendly(server, zone.owner(), team)) { clear(player); return; }

        // Игрок-враг внутри чужой зоны — ведём отсчёт.
        UUID id = player.getUUID();
        Integer prev = COUNTDOWN.get(id);
        int remaining = (prev == null) ? Config.getBaseZoneCountdownSeconds() * 20 : prev - 1;

        if (remaining <= 0) {
            COUNTDOWN.remove(id);
            kill(player, level, zone.owner());
            return;
        }

        COUNTDOWN.put(id, remaining);
        if (prev == null || remaining % 20 == 0) {
            int secondsLeft = (remaining + 19) / 20; // ceil до целых секунд
            sendWarning(player, secondsLeft);
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        COUNTDOWN.remove(player.getUUID());
    }

    // --- Синхронизация зон на карту (S→C) ---

    private static int broadcastCheckCounter = 0;
    private static int lastBroadcastSig = 0;

    private static BaseZoneMapSyncPacket buildPacket(MinecraftServer server) {
        List<BaseZoneView> views = new ArrayList<>();
        for (BaseZone z : BaseZoneSavedData.get(server).zones()) {
            if (!z.isComplete()) continue;
            views.add(new BaseZoneView(z.displayName(), z.dimension(), z.owner(),
                    Teams.color(server, z.owner()), z.minX(), z.minZ(), z.maxX(), z.maxZ()));
        }
        return new BaseZoneMapSyncPacket(views);
    }

    /** Первичная синхронизация зон конкретному игроку (логин). */
    public static void sendTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null) PjmNetworking.sendToPlayer(player, buildPacket(server));
    }

    /** Раз в секунду: если набор зон изменился — разослать всем. Зоны меняются редко (OP-команды). */
    public static void broadcastIfChanged(MinecraftServer server) {
        if (++broadcastCheckCounter < 20) return;
        broadcastCheckCounter = 0;
        BaseZoneMapSyncPacket packet = buildPacket(server);
        int sig = packet.zones().hashCode();
        if (sig == lastBroadcastSig) return;
        lastBroadcastSig = sig;
        PjmNetworking.sendToAll(server, packet);
    }

    /**
     * Неуязвимость защитников внутри своей зоны базы: игрок, стоящий в зоне, чей {@code owner}
     * совпадает с его scoreboard-командой, не получает урон **ни от одного игрока** — ни от
     * союзника (тимкилл), ни от врага (обстрел базы снаружи).
     *
     * <p>Отменяется только урон, инициированный игроком. Мобы, падение, окружение и отсчёт
     * {@link #BASE_ZONE_DAMAGE} действуют как обычно. Урон самому себе (в т.ч. от своей
     * взрывчатки) тоже не отменяется. Урон, который наносит сам защитник, не затрагивается —
     * стрелять из зоны наружу можно.</p>
     *
     * @param attacker сущность-инициатор урона ({@code DamageSource.getEntity()} — для снарядов это стрелок)
     * @param victim   пострадавший игрок
     * @return {@code true}, если урон нужно отменить
     */
    public static boolean shouldCancelPlayerDamage(Entity attacker, ServerPlayer victim) {
        if (!Config.isBaseZoneEnabled()) return false;
        if (!(attacker instanceof ServerPlayer) || attacker == victim) return false;

        String victimTeam = Teams.resolvePlayerTeamId(victim);
        if (victimTeam == null || victimTeam.isBlank()) return false;

        MinecraftServer server = victim.getServer();
        if (server == null) return false;
        String dimension = victim.serverLevel().dimension().location().toString();
        BaseZone zone = BaseZoneSavedData.get(server).findZoneAt(dimension, victim.blockPosition());
        return zone != null && Alliances.friendly(server, zone.owner(), victimTeam);
    }

    /**
     * Отключение урона от взрывов (SBW) и гранат (WarBorn) внутри любой зоны базы. Защищает
     * защитников базы от закидывания взрывчаткой. Ловит:
     * <ul>
     *   <li>любой тип урона с тегом {@link DamageTypeTags#IS_EXPLOSION} — ванильный взрыв гранаты
     *       WarBorn ({@code minecraft:explosion}/{@code player_explosion}) и большинство взрывов SBW
     *       ({@code projectile_explosion}, {@code custom_explosion}, {@code lunge_mine});</li>
     *   <li>SBW-типы взрыва вне тега — {@code superbwarfare:vehicle_explosion}, {@code superbwarfare:mine};</li>
     *   <li>любой урон от сущности из мода {@code warbornexplosives} — осколки ({@code ShrapnelEntity},
     *       тип {@code minecraft:thrown}) и сама граната.</li>
     * </ul>
     *
     * @param source источник урона
     * @param victim пострадавший игрок
     * @return {@code true}, если урон нужно отменить
     */
    public static boolean shouldCancelExplosion(DamageSource source, ServerPlayer victim) {
        if (!Config.isBaseZoneEnabled() || !Config.isBaseZoneBlockExplosions()) return false;
        if (!isExplosiveSource(source)) return false;

        MinecraftServer server = victim.getServer();
        if (server == null) return false;
        String dimension = victim.serverLevel().dimension().location().toString();
        return BaseZoneSavedData.get(server).findZoneAt(dimension, victim.blockPosition()) != null;
    }

    /**
     * Серверный гейт для стратегического удара: круг поражения в XZ не должен пересекать
     * ни одну завершённую базовую зону. Y намеренно игнорируется — цель выбирается на 2D-карте.
     */
    public static boolean intersectsProtectedStrikeArea(MinecraftServer server, String dimension,
                                                        double x, double z, double radius) {
        if (server == null || dimension == null) return false;
        double safeRadius = Math.max(0.0, radius);
        for (BaseZone zone : BaseZoneSavedData.get(server).zones()) {
            if (!zone.isComplete() || !zone.dimension().equals(dimension)) continue;
            if (ru.liko.pjmbasemod.common.missile.MissileTargetPolicy.circleIntersectsRectangle(
                    x, z, safeRadius, zone.minX(), zone.minZ(), zone.maxX(), zone.maxZ())) return true;
        }
        return false;
    }

    private static boolean isExplosiveSource(DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return true;

        ResourceLocation typeId = source.typeHolder().unwrapKey()
                .map(ResourceKey::location).orElse(null);
        if (typeId != null && "superbwarfare".equals(typeId.getNamespace())) {
            String path = typeId.getPath();
            if (path.contains("explosion") || path.contains("mine")) return true;
        }

        Entity direct = source.getDirectEntity();
        if (direct != null) {
            ResourceLocation entId = BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType());
            if ("warbornexplosives".equals(entId.getNamespace())) return true;
        }
        return false;
    }

    private static void clear(ServerPlayer player) {
        if (COUNTDOWN.remove(player.getUUID()) != null) {
            player.connection.send(new ClientboundClearTitlesPacket(false));
        }
    }

    private static void sendWarning(ServerPlayer player, int secondsLeft) {
        // stay=30 тиков > 20: титр не гаснет между ежесекундными обновлениями.
        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 5));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("basezone.warning.title")
                        .withStyle(ChatFormatting.RED)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable("basezone.warning.subtitle", secondsLeft)
                        .withStyle(ChatFormatting.YELLOW)));
    }

    private static void kill(ServerPlayer player, ServerLevel level, String zoneOwner) {
        player.connection.send(new ClientboundClearTitlesPacket(false));
        DamageSource source = level.damageSources().source(BASE_ZONE_DAMAGE);
        player.hurt(source, Float.MAX_VALUE);
        ru.liko.pjmbasemod.common.logging.PjmActionLogger.instance().logSubsystem(
                ru.liko.pjmbasemod.common.logging.LogCategory.BASEZONE,
                player.getGameProfile().getName() + " погиб в базовой зоне команды " + zoneOwner);
    }
}
