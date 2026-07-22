package ru.liko.pjmbasemod.common.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

/** Безопасный гейт опциональной интеграции ракетного взрыва с SuperbWarfare. */
public final class SbwMissileCompat {

    private SbwMissileCompat() {}

    public static boolean available() {
        return ModList.get().isLoaded("superbwarfare");
    }

    public static void detonate(Entity missile, @Nullable Entity attacker, Vec3 position,
                                float damage, float radius, boolean destroyBlocks) {
        if (!available()) return;
        SbwMissileExplosionIntegration.detonate(
                missile, attacker, position, damage, radius, destroyBlocks);
    }
}
