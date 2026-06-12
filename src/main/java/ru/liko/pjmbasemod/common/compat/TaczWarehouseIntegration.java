package ru.liko.pjmbasemod.common.compat;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.init.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/** Actual TACZ calls. Only touch this class after confirming the TACZ mod is loaded. */
final class TaczWarehouseIntegration {

    private static final Map<ResourceLocation, ResourceLocation> LEGACY_GUN_ALIASES = Map.of(
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "ak_47"),
            ResourceLocation.fromNamespaceAndPath("tacz", "ak47")
    );

    private TaczWarehouseIntegration() {}

    static boolean canResolve(ResourceLocation configuredId) {
        return resolveTaczId(configuredId) != null;
    }

    static ItemStack createStack(ResourceLocation configuredId, int count) {
        ResourceLocation taczId = resolveTaczId(configuredId);
        if (taczId == null) return ItemStack.EMPTY;

        int safeCount = Math.max(1, count);
        if (TimelessAPI.getCommonGunIndex(taczId).isPresent()) {
            ItemStack gun = new ItemStack(ModItems.MODERN_KINETIC_GUN.get(), safeCount);
            if (gun.getItem() instanceof IGun iGun) {
                iGun.setGunId(gun, taczId);
            }
            return gun;
        }

        var ammoIndex = TimelessAPI.getCommonAmmoIndex(taczId);
        if (ammoIndex.isPresent()) {
            ItemStack ammo = AmmoItemBuilder.create().setId(taczId).setCount(safeCount).build();
            ammo.set(DataComponents.MAX_STACK_SIZE, ammoIndex.get().getStackSize());
            return ammo;
        }

        if (TimelessAPI.getCommonAttachmentIndex(taczId).isPresent()) {
            return AttachmentItemBuilder.create().setId(taczId).setCount(safeCount).build();
        }

        return ItemStack.EMPTY;
    }

    static boolean matches(ItemStack stack, ResourceLocation configuredId) {
        if (stack.isEmpty()) return false;
        ResourceLocation taczId = resolveTaczId(configuredId);
        if (taczId == null) return false;

        if (stack.getItem() instanceof IGun iGun && TimelessAPI.getCommonGunIndex(taczId).isPresent()) {
            return taczId.equals(iGun.getGunId(stack));
        }
        if (stack.getItem() instanceof IAmmo iAmmo && TimelessAPI.getCommonAmmoIndex(taczId).isPresent()) {
            return taczId.equals(iAmmo.getAmmoId(stack));
        }
        if (stack.getItem() instanceof IAttachment attachment && TimelessAPI.getCommonAttachmentIndex(taczId).isPresent()) {
            return taczId.equals(attachment.getAttachmentId(stack));
        }
        return false;
    }

    @Nullable
    private static ResourceLocation resolveTaczId(ResourceLocation configuredId) {
        for (ResourceLocation candidate : candidates(configuredId)) {
            if (TimelessAPI.getCommonGunIndex(candidate).isPresent()
                    || TimelessAPI.getCommonAmmoIndex(candidate).isPresent()
                    || TimelessAPI.getCommonAttachmentIndex(candidate).isPresent()) {
                return candidate;
            }
        }
        return null;
    }

    private static List<ResourceLocation> candidates(ResourceLocation configuredId) {
        ResourceLocation alias = LEGACY_GUN_ALIASES.get(configuredId);
        ResourceLocation taczWithoutUnderscores = ResourceLocation.fromNamespaceAndPath(
                "tacz", configuredId.getPath().replace("_", ""));
        return alias == null
                ? List.of(configuredId, taczWithoutUnderscores)
                : List.of(configuredId, alias, taczWithoutUnderscores);
    }
}
