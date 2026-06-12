package ru.liko.pjmbasemod.common.frontline;

import net.minecraft.nbt.CompoundTag;

public final class FrontlineSectorState {

    private final FrontlineSectorKey key;
    private String captureTeamId;
    private int progressTicks;

    public FrontlineSectorState(FrontlineSectorKey key) {
        this(key, "", 0);
    }

    public FrontlineSectorState(FrontlineSectorKey key, String captureTeamId, int progressTicks) {
        this.key = key;
        this.captureTeamId = normalize(captureTeamId);
        this.progressTicks = Math.max(0, progressTicks);
    }

    public FrontlineSectorKey key() {
        return key;
    }

    public String captureTeamId() {
        return captureTeamId;
    }

    public void setCaptureTeamId(String captureTeamId) {
        this.captureTeamId = normalize(captureTeamId);
    }

    public int progressTicks() {
        return progressTicks;
    }

    public void setProgressTicks(int progressTicks) {
        this.progressTicks = Math.max(0, progressTicks);
    }

    public void clearCapture() {
        this.captureTeamId = "";
        this.progressTicks = 0;
    }

    public boolean hasProgress() {
        return !captureTeamId.isBlank() && progressTicks > 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", key.dimension());
        tag.putString("region", key.regionName());
        tag.putInt("x", key.x());
        tag.putInt("z", key.z());
        tag.putString("captureTeam", captureTeamId);
        tag.putInt("progressTicks", progressTicks);
        return tag;
    }

    public static FrontlineSectorState load(CompoundTag tag) {
        FrontlineSectorKey key = new FrontlineSectorKey(
                tag.getString("dimension"),
                tag.getString("region"),
                tag.getInt("x"),
                tag.getInt("z")
        );
        return new FrontlineSectorState(key, tag.getString("captureTeam"), tag.getInt("progressTicks"));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
