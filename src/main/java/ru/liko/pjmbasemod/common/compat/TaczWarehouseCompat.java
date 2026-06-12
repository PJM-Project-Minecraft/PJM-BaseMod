package ru.liko.pjmbasemod.common.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

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
}
