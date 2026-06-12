package ru.liko.pjmbasemod.common.faction;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.Config;

import java.util.List;

/**
 * Выполняет настроенные в конфиге команды при выборе игроком фракции (например телепорт на базу).
 * Команды берутся из {@link Config#getTeamJoinCommands(String)} и исполняются от лица игрока с
 * правами оператора и подавленным выводом в чат, поэтому {@code @s} и {@code ~ ~ ~} указывают на игрока.
 */
public final class FactionJoinActions {

    private FactionJoinActions() {
    }

    public static void run(ServerPlayer player, String teamId) {
        if (player == null || player.getServer() == null) return;
        List<String> commands = Config.getTeamJoinCommands(teamId);
        if (commands.isEmpty()) return;

        MinecraftServer server = player.getServer();
        // Источник захватывается в момент вызова — игрок уже выведен из лобби в обычный мир.
        CommandSourceStack source = player.createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
        for (String command : commands) {
            // performPrefixedCommand сам отбрасывает ведущий '/', если он есть.
            server.getCommands().performPrefixedCommand(source, command);
        }
    }
}
