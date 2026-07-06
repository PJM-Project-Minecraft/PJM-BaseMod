package ru.liko.pjmbasemod.common.basezone;

import net.minecraft.ChatFormatting;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;

import java.util.HashMap;
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

        String team = FrontlineTeams.resolvePlayerTeamId(player);
        if (team != null && zone.owner().equalsIgnoreCase(team)) { clear(player); return; }

        // Игрок-враг внутри чужой зоны — ведём отсчёт.
        UUID id = player.getUUID();
        Integer prev = COUNTDOWN.get(id);
        int remaining = (prev == null) ? Config.getBaseZoneCountdownSeconds() * 20 : prev - 1;

        if (remaining <= 0) {
            COUNTDOWN.remove(id);
            kill(player, level);
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

    private static void kill(ServerPlayer player, ServerLevel level) {
        player.connection.send(new ClientboundClearTitlesPacket(false));
        DamageSource source = level.damageSources().source(BASE_ZONE_DAMAGE);
        player.hurt(source, Float.MAX_VALUE);
    }
}
