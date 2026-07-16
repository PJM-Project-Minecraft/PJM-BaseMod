package ru.liko.pjmbasemod.common.compat;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

/**
 * Имя оружия TACZ из {@link DamageSource} для лога убийств: прямая сущность урона —
 * пуля {@link EntityKineticBullet}, у неё есть {@code getGunId()}.
 *
 * <p>TACZ — compileOnly: все импорты {@code com.tacz.*} изолированы во вложенном {@link Impl},
 * который класслоадится только после проверки {@code ModList.isLoaded("tacz")}.</p>
 */
public final class TaczKillWeaponCompat {

    private TaczKillWeaponCompat() {}

    @Nullable
    public static String resolve(DamageSource source) {
        if (!ModList.get().isLoaded("tacz")) return null;
        try {
            return Impl.resolve(source);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class Impl {
        static String resolve(DamageSource source) {
            if (!(source.getDirectEntity() instanceof EntityKineticBullet bullet)) return null;
            ResourceLocation gunId = bullet.getGunId();
            if (gunId == null) return null;
            // Имена стволов лежат в lang ганпака (внешние файлы, на сервере обычно недоступны) —
            // пробуем перевод, иначе показываем id ствола (ak47 и т.п.), это читаемо.
            String key = gunId.getNamespace() + ".gun." + gunId.getPath() + ".name";
            Language lang = Language.getInstance();
            return lang.has(key) ? lang.getOrDefault(key) : gunId.getPath();
        }
    }
}
