package ru.liko.pjmbasemod.common.missile;

import java.util.Locale;

/**
 * Серверный JSON-профиль стратегической ракеты.
 *
 * <p>Поля package-private намеренно: Gson заполняет модель напрямую, а остальной код
 * получает только нормализованные значения через методы доступа.</p>
 */
public final class MissileDefinition {

    public enum Trajectory { CRUISE, BALLISTIC }

    String id = "missile";
    String displayName = "Ракета";
    String translationKey = "";
    String trajectory = "cruise";
    boolean enabled = true;
    int supplyCost = 25;
    int cooldownSeconds = 600;
    int flightSeconds = 10;
    int spawnDistance = 128;
    int cruiseHeight = 28;
    int terminalDiveDistance = 36;
    int ballisticApex = 160;
    float weaveAmplitude = 0.0f;
    float weaveCycles = 1.0f;
    float damage = 120.0f;
    float radius = 8.0f;
    float hitPoints = 35.0f;
    float shotDownPower = 0.35f;
    boolean destroyBlocks = false;

    public MissileDefinition() {}

    public static MissileDefinition create(
            String id, String displayName, Trajectory trajectory,
            int supplyCost, int cooldownSeconds, int flightSeconds,
            int spawnDistance, int cruiseHeight, int terminalDiveDistance, int ballisticApex,
            float damage, float radius, float hitPoints, float shotDownPower, boolean destroyBlocks) {
        MissileDefinition definition = new MissileDefinition();
        definition.id = id;
        definition.displayName = displayName;
        definition.translationKey = "missile.pjmbasemod." + id;
        definition.trajectory = trajectory.name().toLowerCase(Locale.ROOT);
        definition.supplyCost = supplyCost;
        definition.cooldownSeconds = cooldownSeconds;
        definition.flightSeconds = flightSeconds;
        definition.spawnDistance = spawnDistance;
        definition.cruiseHeight = cruiseHeight;
        definition.terminalDiveDistance = terminalDiveDistance;
        definition.ballisticApex = ballisticApex;
        definition.damage = damage;
        definition.radius = radius;
        definition.hitPoints = hitPoints;
        definition.shotDownPower = shotDownPower;
        definition.destroyBlocks = destroyBlocks;
        definition.normalize();
        return definition;
    }

    public void normalize() {
        id = sanitizeId(id);
        if (displayName == null || displayName.isBlank()) displayName = id;
        else displayName = displayName.trim();
        if (translationKey == null) translationKey = "";
        translationKey = translationKey.trim();
        trajectory = trajectory == null ? "cruise" : trajectory.trim().toLowerCase(Locale.ROOT);
        if (!trajectory.equals("cruise") && !trajectory.equals("ballistic")) trajectory = "cruise";
        supplyCost = clamp(supplyCost, 0, 1_000_000);
        cooldownSeconds = clamp(cooldownSeconds, 0, 86_400);
        flightSeconds = clamp(flightSeconds, 2, 120);
        // Дальний спавн допустим: сущность сама держит runtime-ticket и подгружает чанки по пути.
        spawnDistance = clamp(spawnDistance, 32, 4096);
        cruiseHeight = clamp(cruiseHeight, 4, 160);
        terminalDiveDistance = clamp(terminalDiveDistance, 8, 320);
        ballisticApex = clamp(ballisticApex, 32, 800);
        weaveAmplitude = clamp(weaveAmplitude, 0.0f, 32.0f);
        weaveCycles = clamp(weaveCycles, 0.25f, 6.0f);
        damage = clamp(damage, 1.0f, 10_000.0f);
        radius = clamp(radius, 1.0f, 40.0f);
        hitPoints = clamp(hitPoints, 1.0f, 10_000.0f);
        shotDownPower = clamp(shotDownPower, 0.0f, 1.0f);
    }

    public boolean isValid() {
        return !id.isBlank() && !displayName.isBlank();
    }

    private static String sanitizeId(String raw) {
        if (raw == null) return "";
        String clean = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("^_+|_+$", "");
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String translationKey() { return translationKey; }
    public Trajectory trajectoryType() { return trajectory.equals("ballistic") ? Trajectory.BALLISTIC : Trajectory.CRUISE; }
    public boolean enabled() { return enabled; }
    public int supplyCost() { return supplyCost; }
    public int cooldownSeconds() { return cooldownSeconds; }
    public int flightSeconds() { return flightSeconds; }
    public int spawnDistance() { return spawnDistance; }
    public int cruiseHeight() { return cruiseHeight; }
    public int terminalDiveDistance() { return terminalDiveDistance; }
    public int ballisticApex() { return ballisticApex; }
    public float damage() { return damage; }
    public float radius() { return radius; }
    public float hitPoints() { return hitPoints; }
    public float shotDownPower() { return shotDownPower; }
    public boolean destroyBlocks() { return destroyBlocks; }

    public float weaveAmplitude() { return weaveAmplitude; }
    public float weaveCycles() { return weaveCycles; }

    MissileDefinition withApproach(float amplitude, float cycles) {
        this.weaveAmplitude = amplitude;
        this.weaveCycles = cycles;
        normalize();
        return this;
    }
}
