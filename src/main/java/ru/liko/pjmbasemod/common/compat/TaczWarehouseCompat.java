package ru.liko.pjmbasemod.common.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.Map;

/** Safe optional TACZ bridge for warehouse entries that point to TACZ virtual gunpack ids. */
public final class TaczWarehouseCompat {

    private TaczWarehouseCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded("tacz");
    }

    public static boolean canResolve(ResourceLocation configuredId) {
        return isLoaded() && TaczWarehouseIntegration.canResolve(configuredId);
    }

    public static ItemStack createStack(ResourceLocation configuredId, int count) {
        return isLoaded() ? TaczWarehouseIntegration.createStack(configuredId, count) : ItemStack.EMPTY;
    }

    public static boolean matches(ItemStack stack, ResourceLocation configuredId) {
        return isLoaded() && TaczWarehouseIntegration.matches(stack, configuredId);
    }

    /** true, если TACZ загружен и предмет — TACZ-ствол (IGun). */
    public static boolean isGun(ItemStack stack) {
        return isLoaded() && TaczWarehouseIntegration.isGun(stack);
    }

    /**
     * Считывает реальный TACZ-id «простого» предмета (патрон/обвес) — у которого один базовый Item,
     * а конкретика в NBT. Возвращает {@code null}, если TACZ не загружен или стек — не патрон/обвес.
     * Стволы не обрабатывает (для них {@link #captureGun}). Сборка по id — через {@link #createStack}.
     */
    @Nullable
    public static String captureSimpleTaczId(ItemStack stack) {
        return isLoaded() ? TaczWarehouseIntegration.captureSimpleTaczId(stack) : null;
    }

    /**
     * Считанные из стека данные TACZ-ствола в декларативной форме (для записи в конфиг склада).
     * Plain-record без ссылок на классы TACZ — безопасен для сборок без мода.
     */
    public record CapturedGun(String gunId, int ammo, String fireMode, boolean ammoInBarrel,
                              Map<String, String> attachments) {}

    /**
     * Считывает данные TACZ-ствола из стека (gunId, патроны, режим огня, патрон в стволе, обвесы).
     * Возвращает {@code null}, если TACZ не загружен или стек — не ствол.
     */
    public static CapturedGun captureGun(ItemStack stack) {
        return isLoaded() ? TaczWarehouseIntegration.captureGun(stack) : null;
    }

    /** Собирает декларативно описанный TACZ-ствол (id, патроны, режим огня, обвесы по слотам). */
    public static ItemStack createGun(HolderLookup.Provider lookup, String gunId, int ammo, String fireMode,
                                      Boolean ammoInBarrel, Map<String, String> attachments, int count) {
        return isLoaded()
                ? TaczWarehouseIntegration.createGun(lookup, gunId, ammo, fireMode, ammoInBarrel, attachments, count)
                : ItemStack.EMPTY;
    }
}
