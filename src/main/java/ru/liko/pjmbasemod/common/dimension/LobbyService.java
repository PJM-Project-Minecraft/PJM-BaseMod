package ru.liko.pjmbasemod.common.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.liko.pjmbasemod.common.faction.FactionMenuService;

/**
 * Логика lobby-дименшена: до выбора фракции игрок отложенно телепортируется на пустую
 * void-платформу, где открывается меню выбора, а после подтверждения возвращается в обычный мир.
 */
public final class LobbyService {

    /** Центр платформы и точка спавна (верх платформы на y=100, игрок стоит на y=101). */
    private static final int PLATFORM_Y = 100;
    private static final int PLATFORM_RADIUS = 4; // 9x9 платформа
    private static final double SPAWN_X = 0.5;
    private static final double SPAWN_Y = 101.0;
    private static final double SPAWN_Z = 0.5;

    private LobbyService() {
    }

    /**
     * Вызывается каждый тик из PjmServerEvents. Если игрок ещё не выбрал фракцию и не находится
     * в лобби — строит платформу и телепортирует его туда. Намеренно не делается в момент логина,
     * т.к. на PlayerLoggedInEvent игрок ещё досоздаётся, а cross-dim teleport требует живой entity.
     */
    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        if (!FactionMenuService.needsFirstJoinSelection(player)) return;
        if (player.level().dimension() == PjmDimensions.LOBBY) return;
        if (player.isDeadOrDying() || player.isRemoved()) return;

        ServerLevel lobby = player.getServer().getLevel(PjmDimensions.LOBBY);
        if (lobby == null) return;

        ensurePlatform(lobby);
        player.setInvulnerable(true);
        player.teleportTo(lobby, SPAWN_X, SPAWN_Y, SPAWN_Z, 0.0F, 0.0F);
    }

    /**
     * Возвращает игрока из лобби в overworld (на мировой спавн). Вызывается после подтверждения
     * выбора фракции. Срабатывает только если игрок действительно в лобби, чтобы переназначение
     * фракции админом из обычного мира не выкидывало его на спавн.
     */
    public static void returnToOverworld(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        if (player.level().dimension() != PjmDimensions.LOBBY) return;

        MinecraftServer server = player.getServer();
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        double y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                spawn.getX(), spawn.getZ());

        player.setInvulnerable(false);
        player.teleportTo(overworld, spawn.getX() + 0.5, y, spawn.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }

    /**
     * Идемпотентно строит платформу под точкой спавна. Проверка по блоку (а не по флагу) корректно
     * переживает перезагрузку мира.
     */
    private static void ensurePlatform(ServerLevel lobby) {
        BlockPos center = new BlockPos(0, PLATFORM_Y, 0);
        if (!lobby.getBlockState(center).isAir()) return;

        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState edge = Blocks.BARRIER.defaultBlockState();
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                lobby.setBlock(new BlockPos(dx, PLATFORM_Y, dz), floor, 3);
                // Невидимый барьерный бортик по периметру, чтобы нельзя было упасть в void.
                if (Math.abs(dx) == PLATFORM_RADIUS || Math.abs(dz) == PLATFORM_RADIUS) {
                    lobby.setBlock(new BlockPos(dx, PLATFORM_Y + 1, dz), edge, 3);
                    lobby.setBlock(new BlockPos(dx, PLATFORM_Y + 2, dz), edge, 3);
                }
            }
        }
    }
}
