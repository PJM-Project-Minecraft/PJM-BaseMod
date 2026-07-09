package ru.liko.pjmbasemod.common.web;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import ru.liko.pjmbasemod.common.teams.Teams;
import ru.liko.pjmbasemod.common.moderation.ModerationSavedData;
import ru.liko.pjmbasemod.common.moderation.ModerationService;
import ru.liko.pjmbasemod.common.role.RoleService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сборка иммутабельных снапшотов игроков и entity. Вызывается ТОЛЬКО на server
 * thread (из MetricsCollector, раз в 2 секунды); результат публикуется в WebState
 * для чтения HTTP/WS-потоками. Стоимость — один проход по всем entity всех уровней.
 */
public final class WebSnapshots {

    private WebSnapshots() {}

    public static void rebuild(MinecraftServer server) {
        ModerationSavedData moderation = ModerationSavedData.get(server);

        List<WebDtos.PlayerDto> players = new ArrayList<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ModerationSavedData.ModerationProfile profile = moderation.profile(sp.getUUID());
            String team = Teams.resolvePlayerTeamId(sp);
            players.add(new WebDtos.PlayerDto(
                    sp.getUUID().toString(),
                    sp.getGameProfile().getName(),
                    sp.serverLevel().dimension().location().toString(),
                    round1(sp.getX()), round1(sp.getY()), round1(sp.getZ()),
                    sp.connection.latency(),
                    team == null ? "" : team,
                    RoleService.currentRoleId(sp),
                    ModerationService.isBanned(server, sp.getUUID()),
                    ModerationService.isVoiceMuted(server, sp.getUUID()),
                    ModerationService.isTextMuted(server, sp.getUUID()),
                    profile == null ? 0 : profile.warnCount()));
        }

        List<WebDtos.EntityDto> entities = new ArrayList<>();
        Map<String, Integer> byDim = new HashMap<>();
        Map<String, Integer> byCategory = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().location().toString();
            int count = 0;
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Player) continue;
                count++;
                String category = categoryOf(entity);
                byCategory.merge(category, 1, Integer::sum);
                entities.add(new WebDtos.EntityDto(
                        entity.getUUID().toString(),
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                        entity.getName().getString(),
                        dim,
                        round1(entity.getX()), round1(entity.getY()), round1(entity.getZ()),
                        category));
            }
            byDim.put(dim, count);
        }

        WebState.setPlayers(List.copyOf(players));
        WebState.setEntities(List.copyOf(entities));
        WebState.setEntityCounts(Map.copyOf(byDim), Map.copyOf(byCategory));
    }

    private static String categoryOf(Entity entity) {
        if (entity instanceof Mob) return "mob";
        if (entity instanceof ItemEntity) return "item";
        if (entity instanceof Projectile) return "projectile";
        return "other";
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
