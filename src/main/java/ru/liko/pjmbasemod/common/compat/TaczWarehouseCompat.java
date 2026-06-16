package ru.liko.pjmbasemod.common.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

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

    /** Собирает декларативно описанный TACZ-ствол (id, патроны, режим огня, обвесы по слотам). */
    public static ItemStack createGun(HolderLookup.Provider lookup, String gunId, int ammo, String fireMode,
                                      Boolean ammoInBarrel, Map<String, String> attachments, int count) {
        return isLoaded()
                ? TaczWarehouseIntegration.createGun(lookup, gunId, ammo, fireMode, ammoInBarrel, attachments, count)
                : ItemStack.EMPTY;
    }
}
