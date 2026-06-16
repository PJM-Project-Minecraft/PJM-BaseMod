package ru.liko.pjmbasemod.common.compat;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.init.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.Pjmbasemod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
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

    /** Декларативная сборка ствола: id, патроны, режим огня, дослан ли патрон, обвесы по слотам. */
    static ItemStack createGun(HolderLookup.Provider lookup, String gunIdStr, int ammo, String fireMode,
                               @Nullable Boolean ammoInBarrel, Map<String, String> attachments, int count) {
        ResourceLocation parsed = ResourceLocation.tryParse(gunIdStr);
        if (parsed == null) return ItemStack.EMPTY;
        ResourceLocation gunId = resolveTaczId(parsed);
        if (gunId == null || TimelessAPI.getCommonGunIndex(gunId).isEmpty()) {
            Pjmbasemod.LOGGER.warn("Warehouse: TACZ-ствол '{}' не найден.", gunIdStr);
            return ItemStack.EMPTY;
        }

        GunItemBuilder builder = GunItemBuilder.create()
                .setId(gunId)
                .setCount(Math.max(1, count))
                .setAmmoCount(Math.max(0, ammo));

        FireMode fm = parseFireMode(fireMode);
        if (fm != null) builder.setFireMode(fm);
        builder.setAmmoInBarrel(ammoInBarrel != null ? ammoInBarrel : ammo > 0);

        if (attachments != null) {
            for (Map.Entry<String, String> entry : attachments.entrySet()) {
                AttachmentType type = parseAttachmentType(entry.getKey());
                ResourceLocation attachmentId = ResourceLocation.tryParse(entry.getValue());
                if (type == null || type == AttachmentType.NONE || attachmentId == null) {
                    Pjmbasemod.LOGGER.warn("Warehouse: обвес '{}'='{}' для ствола '{}' пропущен (неизвестный слот или id).",
                            entry.getKey(), entry.getValue(), gunIdStr);
                    continue;
                }
                builder.putAttachment(type, attachmentId);
            }
        }
        return builder.build(lookup);
    }

    @Nullable
    private static FireMode parseFireMode(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return FireMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static AttachmentType parseAttachmentType(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return AttachmentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
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
