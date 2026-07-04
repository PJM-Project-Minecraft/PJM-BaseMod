package ru.liko.pjmbasemod.common.web;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.moderation.ModerationSavedData;
import ru.liko.pjmbasemod.common.moderation.ModerationService;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Мост «HTTP-поток → server thread» для всех действий панели. Каждый метод
 * планирует работу через server.execute() и отдаёт CompletableFuture — хендлер
 * Javalin ждёт результат с таймаутом. Аудит: каждое действие пишется в лог
 * с ником админа из веб-сессии. В историю модерации попадает ServerPlayer
 * модератора, если он сейчас онлайн (иначе "Console" — ограничение ModerationService).
 */
public final class WebActions {

    public record ActionResult(boolean ok, String message) {

        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult error(String message) {
            return new ActionResult(false, message);
        }
    }

    public record HistoryDto(String type, String action, String reason,
                             String moderator, long ts, long durationMs) {}

    private WebActions() {}

    private static <T> CompletableFuture<T> onServer(MinecraftServer server, Supplier<T> task, T fallback) {
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Exception e) {
                Pjmbasemod.LOGGER.error("[WebPanel] действие завершилось ошибкой", e);
                future.complete(fallback);
            }
        });
        return future;
    }

    private static CompletableFuture<ActionResult> action(MinecraftServer server, Supplier<ActionResult> task) {
        return onServer(server, task, ActionResult.error("internal_error"));
    }

    private static void audit(String adminName, String action, String details) {
        Pjmbasemod.LOGGER.info("[WebPanel] {} → {} {}", adminName, action, details);
    }

    /** Модератор для записи в историю: онлайн-игрок админа или null (→ "Console"). */
    @Nullable
    private static ServerPlayer moderatorOrNull(MinecraftServer server, @Nullable UUID adminId) {
        return adminId == null ? null : server.getPlayerList().getPlayer(adminId);
    }

    // ---------------------------------------------------------------- модерация

    public static CompletableFuture<ActionResult> kick(MinecraftServer server, UUID target,
                                                       String reason, @Nullable UUID adminId, String adminName) {
        return action(server, () -> {
            ServerPlayer player = server.getPlayerList().getPlayer(target);
            if (player == null) return ActionResult.error("player_offline");
            ModerationService.kick(player, reason, moderatorOrNull(server, adminId));
            audit(adminName, "kick", player.getGameProfile().getName() + " (" + reason + ")");
            return ActionResult.success("kicked");
        });
    }

    /** type ∈ {warn, ban, mute_voice, mute_text}. durationMs игнорируется для warn. */
    public static CompletableFuture<ActionResult> punish(MinecraftServer server, UUID target, String targetName,
                                                         String type, long durationMs, String reason,
                                                         @Nullable UUID adminId, String adminName) {
        return action(server, () -> {
            ServerPlayer moderator = moderatorOrNull(server, adminId);
            switch (type) {
                case "warn" -> ModerationService.warn(server, target, targetName, reason, moderator);
                case "ban" -> ModerationService.applyBan(server, target, targetName, durationMs, reason, moderator);
                case "mute_voice" -> ModerationService.muteVoice(server, target, targetName, durationMs, reason, moderator);
                case "mute_text" -> ModerationService.muteText(server, target, targetName, durationMs, reason, moderator);
                default -> {
                    return ActionResult.error("bad_type");
                }
            }
            audit(adminName, type, targetName + " (" + reason + ")");
            return ActionResult.success(type);
        });
    }

    /** type ∈ {ban, mute_voice, mute_text} — какое наказание снимаем. */
    public static CompletableFuture<ActionResult> pardon(MinecraftServer server, UUID target, String targetName,
                                                         String type, @Nullable UUID adminId, String adminName) {
        return action(server, () -> {
            ServerPlayer moderator = moderatorOrNull(server, adminId);
            boolean removed = switch (type) {
                case "ban" -> ModerationService.pardon(server, target, targetName, moderator);
                case "mute_voice" -> ModerationService.unmuteVoice(server, target, targetName, moderator);
                case "mute_text" -> ModerationService.unmuteText(server, target, targetName, moderator);
                default -> false;
            };
            if (!removed) return ActionResult.error("nothing_to_pardon");
            audit(adminName, "pardon:" + type, targetName);
            return ActionResult.success("pardoned");
        });
    }

    public static CompletableFuture<List<HistoryDto>> moderationHistory(MinecraftServer server, UUID target) {
        return onServer(server, () -> {
            ModerationSavedData.ModerationProfile profile = ModerationSavedData.get(server).profile(target);
            if (profile == null) return List.<HistoryDto>of();
            List<HistoryDto> result = new ArrayList<>();
            for (ModerationSavedData.HistoryEntry h : profile.history()) {
                result.add(new HistoryDto(h.type().id(), h.action(), h.reason(),
                        h.moderatorName(), h.timestampMs(), h.durationMs()));
            }
            return List.copyOf(result);
        }, List.of());
    }

    // ---------------------------------------------------------------- телепорт

    public static CompletableFuture<ActionResult> teleport(MinecraftServer server, UUID target,
                                                           @Nullable UUID toPlayer,
                                                           @Nullable Double x, @Nullable Double y, @Nullable Double z,
                                                           @Nullable String dim, String adminName) {
        return action(server, () -> {
            ServerPlayer player = server.getPlayerList().getPlayer(target);
            if (player == null) return ActionResult.error("player_offline");

            if (toPlayer != null) {
                ServerPlayer dest = server.getPlayerList().getPlayer(toPlayer);
                if (dest == null) return ActionResult.error("destination_offline");
                player.teleportTo(dest.serverLevel(), dest.getX(), dest.getY(), dest.getZ(),
                        player.getYRot(), player.getXRot());
                audit(adminName, "teleport", player.getGameProfile().getName() + " → " + dest.getGameProfile().getName());
                return ActionResult.success("teleported");
            }

            if (x == null || y == null || z == null) return ActionResult.error("bad_coordinates");
            ServerLevel level = player.serverLevel();
            if (dim != null && !dim.isBlank()) {
                level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim)));
                if (level == null) return ActionResult.error("bad_dimension");
            }
            player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
            audit(adminName, "teleport", player.getGameProfile().getName()
                    + " → " + x + " " + y + " " + z + " (" + (dim == null ? "same" : dim) + ")");
            return ActionResult.success("teleported");
        });
    }

    // ---------------------------------------------------------------- entity

    public static CompletableFuture<ActionResult> removeEntities(MinecraftServer server,
                                                                 List<String> uuids, String adminName) {
        return action(server, () -> {
            int removed = 0;
            for (String raw : uuids) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(raw);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = level.getEntity(uuid);
                    if (entity != null && !(entity instanceof Player)) {
                        entity.discard();
                        removed++;
                        break;
                    }
                }
            }
            audit(adminName, "remove_entities", "removed=" + removed + " of " + uuids.size());
            return ActionResult.success("removed:" + removed);
        });
    }

    /** Массовое удаление: по типу и/или радиусу вокруг точки в одном дименшене. Игроки не удаляются никогда. */
    public static CompletableFuture<ActionResult> removeEntitiesBulk(MinecraftServer server,
                                                                     @Nullable String typeId, String dimId,
                                                                     @Nullable Double centerX, @Nullable Double centerZ,
                                                                     @Nullable Double radius, String adminName) {
        return action(server, () -> {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimId)));
            if (level == null) return ActionResult.error("bad_dimension");
            boolean byRadius = centerX != null && centerZ != null && radius != null;
            double radiusSq = byRadius ? radius * radius : 0;

            List<Entity> toRemove = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Player) continue;
                if (typeId != null && !typeId.isBlank()
                        && !BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(typeId)) {
                    continue;
                }
                if (byRadius) {
                    double dx = entity.getX() - centerX;
                    double dz = entity.getZ() - centerZ;
                    if (dx * dx + dz * dz > radiusSq) continue;
                }
                toRemove.add(entity);
            }
            toRemove.forEach(Entity::discard);
            audit(adminName, "remove_entities_bulk",
                    "dim=" + dimId + " type=" + typeId + " radius=" + radius + " removed=" + toRemove.size());
            return ActionResult.success("removed:" + toRemove.size());
        });
    }
}
