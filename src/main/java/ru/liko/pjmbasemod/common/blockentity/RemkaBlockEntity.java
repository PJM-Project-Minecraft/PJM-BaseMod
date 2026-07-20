package ru.liko.pjmbasemod.common.blockentity;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.compat.SbwRepairCompat;
import ru.liko.pjmbasemod.common.fleet.VehicleFleetManager;
import ru.liko.pjmbasemod.common.init.PjmBlockEntities;
import ru.liko.pjmbasemod.common.init.PjmSounds;
import ru.liko.pjmbasemod.common.teams.Teams;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ядро ремонтной станции «Ремка» (аналог Repair Station из Squad).
 *
 * <p>Раз в {@code remka.intervalTicks} чинит технику SuperbWarfare своей фракции в радиусе
 * {@code remka.radius} — корпус и части (башня, гусеницы, двигатели).</p>
 *
 * <p>Саморегенерацию техники в SuperbWarfare при этом положено отключить
 * ({@code vehicle.repair.repair_cooldown = -1}) — иначе Ремка теряет смысл.</p>
 */
public class RemkaBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * Установлен ли SuperbWarfare. Проверяется здесь, а не в {@link SbwRepairCompat}: любое
     * обращение к тому классу подгружает его вместе с типами SBW, поэтому гейт обязан жить
     * снаружи. Значение неизменно в рамках запуска — считаем один раз.
     */
    private static final boolean SBW_LOADED = ModList.get().isLoaded("superbwarfare");

    @Nullable
    private UUID ownerId;

    /** Фракция-владелец: только её технику Ремка чинит. Пусто — чинит технику любой фракции. */
    private String ownerTeamId = "";

    private int tickCounter;

    public RemkaBlockEntity(BlockPos pos, BlockState state) {
        super(PjmBlockEntities.REMKA.get(), pos, state);
    }

    public void setOwner(@Nullable UUID ownerId) {
        this.ownerId = ownerId;
        setChanged();
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerTeamId(@Nullable String teamId) {
        this.ownerTeamId = teamId == null ? "" : Teams.normalize(teamId);
        setChanged();
    }

    public String getOwnerTeamId() {
        return ownerTeamId == null ? "" : ownerTeamId;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RemkaBlockEntity be) {
        if (!SBW_LOADED || !Config.isRemkaEnabled()) return;
        if (++be.tickCounter % Config.getRemkaIntervalTicks() != 0) return;

        if (be.repairNearby(level, pos)) {
            level.playSound(null, pos, PjmSounds.REMKA_REPAIR.get(), SoundSource.BLOCKS,
                    0.7F, 0.95F + level.random.nextFloat() * 0.1F);
        }
    }

    /** Чинит всю подходящую технику вокруг. {@code true}, если хоть что-то починилось. */
    private boolean repairNearby(Level level, BlockPos pos) {
        double radius = Config.getRemkaRadius();
        float hullPercent = (float) Config.getRemkaHullPercentPerCycle();
        float partPercent = (float) Config.getRemkaPartPercentPerCycle();

        List<Entity> nearby = level.getEntities((Entity) null,
                new AABB(pos).inflate(radius), SbwRepairCompat::isVehicle);

        boolean healed = false;
        for (Entity vehicle : nearby) {
            if (vehicle.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > radius * radius) continue;
            if (!isSameTeam(level, vehicle)) continue;
            healed |= SbwRepairCompat.repair(vehicle, hullPercent, partPercent);
        }
        return healed;
    }

    /**
     * Своя ли это техника.
     *
     * <p>Основной источник — гараж: {@link VehicleFleetManager#teamId} помнит фракцию, которой
     * технику выдали, и не протухает. Запасной — последний водитель, для техники не из гаража
     * (заспавнена командой, с карты). Его SBW сбрасывает в {@code "undefined"} через 10 секунд
     * простоя незапертой машины, поэтому опираться на него в первую очередь нельзя: вражеский
     * танк, брошенный рядом, стал бы «ничейным» и чинился бы нашей Ремкой.</p>
     *
     * <p>Технику, чью принадлежность установить не удалось, не чиним — Ремка фракционная,
     * и чинить врага для неё хуже, чем не починить ничейный корпус.</p>
     */
    private boolean isSameTeam(Level level, Entity vehicle) {
        String owner = getOwnerTeamId();
        if (owner.isBlank()) return true;

        String fleetTeam = VehicleFleetManager.teamId(level.getServer(), vehicle);
        if (!fleetTeam.isBlank()) return owner.equals(fleetTeam);

        String uuid = SbwRepairCompat.lastDriverUuid(vehicle);
        if (uuid == null) return false;

        String driverName = resolvePlayerName(level, uuid);
        if (driverName == null) return false;

        return owner.equals(Teams.resolveTeamIdByName(level.getServer(), driverName));
    }

    /** Ник по UUID: сначала онлайн-список, затем кэш профилей (оффлайн-водитель). */
    @Nullable
    private String resolvePlayerName(Level level, String rawUuid) {
        MinecraftServer server = level.getServer();
        if (server == null) return null;

        UUID uuid;
        try {
            uuid = UUID.fromString(rawUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }

        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();

        if (server.getProfileCache() == null) return null;
        Optional<GameProfile> cached = server.getProfileCache().get(uuid);
        return cached.map(GameProfile::getName).orElse(null);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Модель статичная — анимаций нет.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.ownerTeamId = Teams.normalize(tag.getString("OwnerTeam"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putString("OwnerTeam", getOwnerTeamId());
    }
}
