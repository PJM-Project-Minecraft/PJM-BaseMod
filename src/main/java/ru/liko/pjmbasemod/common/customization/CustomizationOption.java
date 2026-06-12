package ru.liko.pjmbasemod.common.customization;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record CustomizationOption(
        String id,
        CustomizationType type,
        Component displayName,
        ResourceLocation icon
) {
    public String getId()              { return id; }
    public CustomizationType getType() { return type; }
    public Component getDisplayName()    { return displayName; }
    public ResourceLocation getIcon()    { return icon; }
}
