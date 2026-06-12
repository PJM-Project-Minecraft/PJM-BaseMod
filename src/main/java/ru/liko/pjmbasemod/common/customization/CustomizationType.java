package ru.liko.pjmbasemod.common.customization;

public enum CustomizationType {
    PLAYER_SKIN("player_skin"),
    HAT("hat"),
    BACKPACK("backpack"),
    PATCH("patch"),
    VEST("vest"),
    BANNER("banner");

    private final String id;
    CustomizationType(String id) { this.id = id; }
    public String getId() { return id; }

    public static CustomizationType byId(String id) {
        for (CustomizationType t : values()) if (t.id.equalsIgnoreCase(id)) return t;
        return PLAYER_SKIN;
    }
}
