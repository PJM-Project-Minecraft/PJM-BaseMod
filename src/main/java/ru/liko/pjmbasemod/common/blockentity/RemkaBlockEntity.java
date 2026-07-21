package ru.liko.pjmbasemod.common.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.compat.SbwRepairCompat;
import ru.liko.pjmbasemod.common.init.PjmBlockEntities;
import ru.liko.pjmbasemod.common.init.PjmSounds;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * Ядро ремонтной станции «Ремка» (аналог Repair Station из Squad).
 *
 * <p>Раз в {@code remka.intervalTicks} чинит любую технику SuperbWarfare в радиусе
 * {@code remka.radius} — корпус и части (башня, гусеницы, двигатели). Принадлежность
 * техники не проверяется: станция чинит всех, кто до неё доехал.</p>
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

    private int tickCounter;

    public RemkaBlockEntity(BlockPos pos, BlockState state) {
        super(PjmBlockEntities.REMKA.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RemkaBlockEntity be) {
        if (!SBW_LOADED || !Config.isRemkaEnabled()) return;
        if (++be.tickCounter % Config.getRemkaIntervalTicks() != 0) return;

        if (repairNearby(level, pos)) {
            level.playSound(null, pos, PjmSounds.REMKA_REPAIR.get(), SoundSource.BLOCKS,
                    0.7F, 0.95F + level.random.nextFloat() * 0.1F);
        }
    }

    /** Чинит всю технику вокруг. {@code true}, если хоть что-то починилось. */
    private static boolean repairNearby(Level level, BlockPos pos) {
        double radius = Config.getRemkaRadius();
        float hullPercent = (float) Config.getRemkaHullPercentPerCycle();
        float partPercent = (float) Config.getRemkaPartPercentPerCycle();

        List<Entity> nearby = level.getEntities((Entity) null,
                new AABB(pos).inflate(radius), SbwRepairCompat::isVehicle);

        boolean healed = false;
        for (Entity vehicle : nearby) {
            if (vehicle.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > radius * radius) continue;
            healed |= SbwRepairCompat.repair(vehicle, hullPercent, partPercent);
        }
        return healed;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Модель статичная — анимаций нет.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
