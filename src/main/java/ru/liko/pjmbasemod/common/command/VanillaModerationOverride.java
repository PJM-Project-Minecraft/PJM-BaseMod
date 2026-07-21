package ru.liko.pjmbasemod.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.moderation.ModerationPermissions;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Перехват ванильных команд модерации ({@code /ban}, {@code /kick}, {@code /pardon},
 * {@code /ban-ip}, {@code /pardon-ip}) — перенаправляет их в систему PJM.
 *
 * <p>Механизм: Brigadier при повторной регистрации literal-ноды <b>мержит</b>, а не заменяет её,
 * поэтому ванильные ноды сначала удаляются из {@link RootCommandNode} рефлексией, а затем
 * регистрируются наши одноимённые. Управляется флагом {@code moderation.overrideVanillaCommands}.
 * Работает с {@link EventPriority#LOWEST}, чтобы отработать после регистрации ванильных/остальных команд.
 * Null-safe: на интегрированном сервере {@code /ban}/{@code /pardon}/{@code /ban-ip} отсутствуют.</p>
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class VanillaModerationOverride {

    private static final String[] OVERRIDDEN = {"ban", "kick", "pardon", "ban-ip", "pardon-ip"};

    private VanillaModerationOverride() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegister(RegisterCommandsEvent event) {
        if (!Config.isModerationOverrideVanilla()) return;
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        RootCommandNode<CommandSourceStack> root = d.getRoot();

        for (String name : OVERRIDDEN) removeCommand(root, name);

        registerBan(d, "ban");
        registerKick(d);
        registerPardon(d);
        registerBanIp(d);
        registerPardonIp(d);
    }

    // ---------------------------------------------------------------- наши замены

    private static void registerBan(CommandDispatcher<CommandSourceStack> d, String literal) {
        d.register(Commands.literal(literal)
                .requires(src -> can(src, ModerationPermissions.BAN))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(ModerationCommands.SUGGEST_TARGETS)
                        .executes(ctx -> ModerationCommands.executeBan(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"), "permanent", "Забанен администратором"))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> ModerationCommands.executeBan(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"), "permanent",
                                        StringArgumentType.getString(ctx, "reason"))))));
    }

    private static void registerKick(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kick")
                .requires(src -> can(src, ModerationPermissions.KICK))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(ModerationCommands.SUGGEST_TARGETS)
                        .executes(ctx -> ModerationCommands.executeKick(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"), "Кикнут администратором"))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> ModerationCommands.executeKick(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "reason"))))));
    }

    private static void registerPardon(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pardon")
                .requires(src -> can(src, ModerationPermissions.BAN))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(ModerationCommands.SUGGEST_TARGETS)
                        .executes(ctx -> {
                            ModerationCommands.ResolvedTarget t = ModerationCommands.resolve(
                                    ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                            return t == null ? 0 : ModerationCommands.executePardon(ctx.getSource(), t);
                        })));
    }

    private static void registerBanIp(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("ban-ip")
                .requires(src -> can(src, ModerationPermissions.BAN))
                .then(Commands.argument("target", StringArgumentType.word()).suggests(ModerationCommands.SUGGEST_TARGETS)
                        .executes(ctx -> ModerationCommands.executeBanIp(ctx.getSource(),
                                StringArgumentType.getString(ctx, "target"), "permanent", "IP-бан администратором"))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> ModerationCommands.executeBanIp(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "target"), "permanent",
                                        StringArgumentType.getString(ctx, "reason"))))));
    }

    private static void registerPardonIp(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pardon-ip")
                .requires(src -> can(src, ModerationPermissions.BAN))
                .then(Commands.argument("ip", StringArgumentType.word())
                        .executes(ctx -> ModerationCommands.executePardonIp(ctx.getSource(),
                                StringArgumentType.getString(ctx, "ip")))));
    }

    // ---------------------------------------------------------------- рефлексивное удаление

    private static void removeCommand(RootCommandNode<CommandSourceStack> root, String name) {
        removeFromMap(root, "children", name);
        removeFromMap(root, "literals", name);
        removeFromMap(root, "arguments", name);
    }

    private static void removeFromMap(CommandNode<CommandSourceStack> node, String fieldName, String key) {
        try {
            Field f = CommandNode.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object value = f.get(node);
            if (value instanceof Map<?, ?> map) {
                map.remove(key);
            }
        } catch (ReflectiveOperationException e) {
            Pjmbasemod.LOGGER.warn("VanillaModerationOverride: не удалось убрать ванильную ноду '{}' ({})",
                    key, e.getMessage());
        }
    }

    private static boolean can(CommandSourceStack source, PermissionNode<Boolean> node) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return ModerationPermissions.can(player, node);
        }
        return source.hasPermission(2);
    }
}
