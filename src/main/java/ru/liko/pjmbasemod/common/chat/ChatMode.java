package ru.liko.pjmbasemod.common.chat;

import net.minecraft.network.chat.Component;

public enum ChatMode {
    LOCAL("local", 0xFFB0BEC5),
    GLOBAL("global", 0xFF4CAF50),
    TEAM("team", 0xFF1976D2);

    private final String id;
    private final int color;

    ChatMode(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String getId()  { return id; }
    public String getKey() { return id; }
    public int    getColor() { return color; }

    public Component getDisplayName() {
        return Component.translatable("gui.pjmbasemod.radial.chat." + id);
    }

    public ChatMode next() {
        ChatMode[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public static ChatMode byId(String id) {
        for (ChatMode m : values()) if (m.id.equalsIgnoreCase(id)) return m;
        return GLOBAL;
    }
}
