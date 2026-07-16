package ru.liko.pjmbasemod.common.logging;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import ru.liko.pjmbasemod.common.compat.SbwVehicleClassifier;
import ru.liko.pjmbasemod.common.compat.TaczKillWeaponCompat;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Человекочитаемое имя оружия убийства из {@link DamageSource} для {@code pjmlogs}.
 *
 * <p>TACZ — через {@link TaczKillWeaponCompat} (compileOnly-зависимость, изолированный класс).
 * SuperbWarfare compile-зависимостью не подключён, поэтому распознаём по имени класса и
 * дёргаем {@code ProjectileEntity.getGunItemId()} рефлексией:
 * пуля стрелкового оружия несёт ключ перевода предмета ({@code item.superbwarfare.ak47}),
 * снаряды техники и сама техника читаемы через описание типа сущности.</p>
 */
public final class KillWeaponResolver {

    private static final String SBW_PROJECTILE_CLASS = "com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity";
    private static final String SBW_NAMESPACE = "superbwarfare";

    @Nullable
    private static volatile Method gunItemIdGetter;

    private KillWeaponResolver() {}

    /** Имя оружия или {@code null}, если источник урона не от TACZ/SBW. */
    @Nullable
    public static String resolve(DamageSource source) {
        if (source == null) return null;
        String tacz = TaczKillWeaponCompat.resolve(source);
        if (tacz != null) return tacz;

        String fromDirect = fromSbwEntity(source.getDirectEntity());
        if (fromDirect != null) return fromDirect;
        // Взрывы SBW: directEntity часто null, снаряд/техника лежит в attacker.
        return fromSbwEntity(source.getEntity());
    }

    @Nullable
    private static String fromSbwEntity(@Nullable Entity entity) {
        if (entity == null) return null;

        // Пуля стрелкового оружия SBW → ключ перевода предмета-ствола.
        Class<?> projectileClass = findClassInHierarchy(entity.getClass(), SBW_PROJECTILE_CLASS);
        if (projectileClass != null) {
            String key = gunItemId(projectileClass, entity);
            if (key != null && !key.isBlank()) return Language.getInstance().getOrDefault(key);
            return typeDescription(entity);
        }

        // Техника SBW (таран, взрыв корпуса) → имя типа техники.
        if (SbwVehicleClassifier.isVehicleEntity(entity)) return typeDescription(entity);

        // Прочие снаряды SBW (пушки, ракеты, гранаты) → имя типа снаряда.
        if (SBW_NAMESPACE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace())) {
            return typeDescription(entity);
        }
        return null;
    }

    @Nullable
    private static String gunItemId(Class<?> projectileClass, Entity entity) {
        try {
            Method getter = gunItemIdGetter;
            if (getter == null) {
                getter = projectileClass.getMethod("getGunItemId");
                getter.setAccessible(true);
                gunItemIdGetter = getter;
            }
            Object value = getter.invoke(entity);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String typeDescription(Entity entity) {
        return entity.getType().getDescription().getString();
    }

    @Nullable
    private static Class<?> findClassInHierarchy(Class<?> start, String name) {
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().equals(name)) return c;
        }
        return null;
    }
}
