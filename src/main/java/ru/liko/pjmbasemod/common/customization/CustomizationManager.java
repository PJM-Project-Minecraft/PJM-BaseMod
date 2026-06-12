package ru.liko.pjmbasemod.common.customization;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.*;

public final class CustomizationManager {

    private static final CustomizationManager INSTANCE = new CustomizationManager();
    public static CustomizationManager getInstance() { return INSTANCE; }

    private final Map<CustomizationType, List<CustomizationOption>> options = new EnumMap<>(CustomizationType.class);

    private CustomizationManager() {
        for (CustomizationType type : CustomizationType.values()) {
            options.put(type, defaults(type));
        }
    }

    private static List<CustomizationOption> defaults(CustomizationType type) {
        List<CustomizationOption> list = new ArrayList<>();
        list.add(new CustomizationOption(
                "default",
                type,
                Component.translatable("gui.pjmbasemod.customization." + type.getId() + ".default"),
                ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID,
                        "textures/icon/customization/" + type.getId() + "_default.png")));
        return list;
    }

    public List<CustomizationOption> getOptionsFor(CustomizationType type) {
        return Collections.unmodifiableList(options.getOrDefault(type, Collections.emptyList()));
    }

    public Optional<CustomizationOption> getOption(CustomizationType type, String id) {
        return getOptionsFor(type).stream().filter(o -> o.id().equalsIgnoreCase(id)).findFirst();
    }

    public CustomizationOption getOrDefault(CustomizationType type, String id) {
        return getOption(type, id).orElseGet(() -> getOptionsFor(type).get(0));
    }

    public void registerOption(CustomizationType type, CustomizationOption option) {
        options.computeIfAbsent(type, t -> new ArrayList<>()).add(option);
    }
}
