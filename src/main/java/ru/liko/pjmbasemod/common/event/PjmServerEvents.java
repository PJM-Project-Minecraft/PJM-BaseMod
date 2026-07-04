package ru.liko.pjmbasemod.common.event;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.antigrief.AntiGriefService;
import ru.liko.pjmbasemod.common.chat.ChatMode;
import ru.liko.pjmbasemod.common.chat.ChatService;
import ru.liko.pjmbasemod.common.customization.SkinRegistry;
import ru.liko.pjmbasemod.common.customization.SkinService;
import ru.liko.pjmbasemod.common.dimension.LobbyService;
import ru.liko.pjmbasemod.common.faction.FactionMenuService;
import ru.liko.pjmbasemod.common.faction.FactionOrderManager;
import ru.liko.pjmbasemod.common.faction.FactionCommanderService;
import ru.liko.pjmbasemod.common.frontline.FrontlineManager;
import ru.liko.pjmbasemod.common.frontline.bluemap.FrontlineBlueMapService;
import ru.liko.pjmbasemod.common.garage.GarageManager;
import ru.liko.pjmbasemod.common.garage.VehicleRegistry;
import ru.liko.pjmbasemod.common.inventory.EquipmentLockService;
import ru.liko.pjmbasemod.common.inventory.InventoryLimitRegistry;
import ru.liko.pjmbasemod.common.inventory.InventoryLimitService;
import ru.liko.pjmbasemod.common.moderation.ModerationService;
import ru.liko.pjmbasemod.common.init.PjmItems;
import ru.liko.pjmbasemod.common.warehouse.CrateRegistry;
import ru.liko.pjmbasemod.common.warehouse.WarehouseItemRegistry;
import ru.liko.pjmbasemod.common.warehouse.WarehouseManager;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.handler.ServerPacketHandlers;
import ru.liko.pjmbasemod.common.network.packet.SyncPjmDataPacket;
import ru.liko.pjmbasemod.common.rank.RankService;
import ru.liko.pjmbasemod.common.region.RegionManager;
import ru.liko.pjmbasemod.common.role.RoleAccessRegistry;
import ru.liko.pjmbasemod.common.role.RoleLimitRegistry;
import ru.liko.pjmbasemod.common.role.RoleService;
import ru.liko.pjmbasemod.common.voice.VoicechatBridge;

import java.util.List;

@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class PjmServerEvents {

    private PjmServerEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (ModerationService.enforceOnLogin(sp)) return; // забанен — кикнут, синхронизировать нечего
        ChatMode mode = ServerPacketHandlers.getChatMode(sp);
        PjmNetworking.sendToPlayer(sp, new SyncPjmDataPacket(sp.getUUID(), mode.getKey()));
        ServerPacketHandlers.sendHudConfig(sp);
        RegionManager.sendInitialSync(sp);
        FrontlineManager.sendInitialSync(sp);
        RankService.sync(sp);
        FactionCommanderService.onPlayerLogin(sp);
        RoleService.onPlayerLogin(sp);
        FactionMenuService.onPlayerLogin(sp);
        FactionOrderManager.syncTo(sp);
        InventoryLimitService.sync(sp);
        SkinService.onPlayerLogin(sp);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (victim.level().isClientSide()) return;

        Entity sourceEntity = event.getSource().getEntity();
        ServerPlayer killer = sourceEntity instanceof ServerPlayer player ? player : null;
        if (killer == null && victim.getKillCredit() instanceof ServerPlayer credited) {
            killer = credited;
        }
        RankService.handlePlayerKill(killer, victim);
    }

    @SubscribeEvent
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Component displayName = FactionCommanderService.tabListName(player);
        if (displayName != null) {
            event.setDisplayName(displayName);
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();
        if (sender == null) return;
        if (ModerationService.isTextMuted(sender.server, sender.getUUID())) {
            event.setCanceled(true);
            sender.sendSystemMessage(Component.literal("§cВы не можете писать в чат: текстовый чат заглушён"));
            return;
        }
        ChatMode mode = ServerPacketHandlers.getChatMode(sender);
        // Все режимы (включая GLOBAL) идут через кастомную доставку, чтобы добавить
        // иконку+ранг к нику. Компромисс: GLOBAL теряет ванильную криптоподпись/репорт.
        event.setCanceled(true);
        ChatService.deliver(sender, mode, event.getMessage());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ServerPacketHandlers.onPlayerLogout(sp);
            ru.liko.pjmbasemod.common.inventory.EquipmentLockService.onPlayerLogout(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FactionCommanderService.onPlayerTick(player);
        RoleService.onPlayerTick(player);
        EquipmentLockService.enforceHeld(player);
        SkinService.onPlayerTick(player);
        LobbyService.onPlayerTick(player);
        FactionMenuService.onPlayerTick(player);
        if (player.tickCount % Math.max(1, InventoryLimitRegistry.get().config().enforceEveryTicks()) == 0) {
            InventoryLimitService.enforce(player);
        }
        if (!Config.isDisableHunger()) return;
        disableHunger(player);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (EquipmentLockService.shouldCancelUse(player, player.getMainHandItem())) {
            event.setCanceled(true);
            return;
        }

        ru.liko.pjmbasemod.common.garage.GarageType garageType;
        if (player.getMainHandItem().is(PjmItems.NOTEBOOK.get())) {
            garageType = ru.liko.pjmbasemod.common.garage.GarageType.GROUND;
        } else if (player.getMainHandItem().is(PjmItems.NOTEBOOK_AIR.get())) {
            garageType = ru.liko.pjmbasemod.common.garage.GarageType.AVIATION;
        } else {
            return;
        }

        if (GarageManager.storeVehicle(player, event.getTarget(), garageType)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (EquipmentLockService.shouldCancelUse(player, event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Анти-гриф: запрещаем только взаимодействие с блоком (useBlock), не отменяя
        // событие целиком — использование предмета в руке (еда, оружие) остаётся доступным.
        if (!AntiGriefService.checkInteract(player, event.getLevel().getBlockState(event.getPos()))) {
            event.setUseBlock(TriState.FALSE);
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (EquipmentLockService.shouldCancelUse(player, event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!AntiGriefService.checkBreak(player, event.getState())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!AntiGriefService.checkPlace(player, event.getPlacedBlock())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        if (EquipmentLockService.shouldCancelUse(player, player.getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FrontlineBlueMapService.onServerStopping();
        VoicechatBridge.onServerStop();
    }

    private static int warehouseScanCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        FrontlineManager.onServerTick(event.getServer());
        FrontlineBlueMapService.onServerTick(event.getServer());
        FactionOrderManager.onServerTick(event.getServer());
        ModerationService.tick(event.getServer());

        if (++warehouseScanCounter >= 20) {
            warehouseScanCounter = 0;
            for (net.minecraft.server.level.ServerLevel level : event.getServer().getAllLevels()) {
                WarehouseManager.scanReceptionZones(level);
                WarehouseManager.renderReceptionZones(level);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        FrontlineBlueMapService.onServerStarted(event.getServer());
        ru.liko.pjmbasemod.common.compat.SbwVehicleClassifier.reload(event.getServer());
        VehicleRegistry.get().reload();
        WarehouseItemRegistry.get().reload();
        ru.liko.pjmbasemod.common.inventory.EquipmentRoleIndex.get().rebuild();
        CrateRegistry.get().reload();
        RoleLimitRegistry.get().reload();
        RoleAccessRegistry.get().reload();
        InventoryLimitRegistry.get().reload();
        SkinRegistry.get().reload();
        RankService.onServerStarted(event.getServer());

        List<? extends String> commands = Config.getStartupCommands();
        if (commands.isEmpty()) return;

        var server = event.getServer();
        var source = server.createCommandSourceStack().withSuppressedOutput();
        var dispatcher = server.getCommands().getDispatcher();
        int executed = 0;

        for (String raw : commands) {
            String command = raw.trim();
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isBlank()) continue;

            try {
                dispatcher.execute(command, source);
                executed++;
            } catch (Exception e) {
                Pjmbasemod.LOGGER.error("Failed to execute startup command from config: {}", raw, e);
            }
        }

        Pjmbasemod.LOGGER.info("PJM-BaseMod: executed {} startup command(s) from config.", executed);
    }

    private static void disableHunger(ServerPlayer player) {
        FoodData food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(0.0F);
        food.setExhaustion(0.0F);
    }
}
