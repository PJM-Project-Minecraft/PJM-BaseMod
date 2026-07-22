package ru.liko.pjmbasemod.common.compat;

import com.atsuishio.superbwarfare.tools.CustomExplosion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Единственный класс ракетной подсистемы с прямой compileOnly-ссылкой на SBW.
 * Вызывать только через {@link SbwMissileCompat} после проверки наличия мода.
 */
final class SbwMissileExplosionIntegration {

    private SbwMissileExplosionIntegration() {}

    static void detonate(Entity missile, @Nullable Entity attacker, Vec3 position,
                         float damage, float radius, boolean destroyBlocks) {
        CustomExplosion.Builder builder = new CustomExplosion.Builder(missile)
                .source(missile)
                .attacker(attacker)
                .damage(damage)
                .radius(radius)
                .position(position);
        if (!destroyBlocks) builder.keepBlock();
        builder.explode();
    }
}
