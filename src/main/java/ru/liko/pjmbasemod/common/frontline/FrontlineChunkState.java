package ru.liko.pjmbasemod.common.frontline;

import net.minecraft.nbt.CompoundTag;

public final class FrontlineChunkState {

    private final FrontlineChunkKey key;
    private String ownerTeamId;
    private String captureTeamId;
    private int progressTicks;

    public FrontlineChunkState(FrontlineChunkKey key) {
        this(key, "", "", 0);
    }

    public FrontlineChunkState(FrontlineChunkKey key, String ownerTeamId, String captureTeamId, int progressTicks) {
        this.key = key;
        this.ownerTeamId = normalize(ownerTeamId);
        this.captureTeamId = normalize(captureTeamId);
        this.progressTicks = Math.max(0, progressTicks);
    }

    public FrontlineChunkKey key() {
        return key;
    }

    public String ownerTeamId() {
        return ownerTeamId;
    }

    public void setOwnerTeamId(String ownerTeamId) {
        this.ownerTeamId = normalize(ownerTeamId);
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
        tag.putInt("x", key.x());
        tag.putInt("z", key.z());
        tag.putString("owner", ownerTeamId);
        tag.putString("captureTeam", captureTeamId);
        tag.putInt("progressTicks", progressTicks);
        return tag;
    }

    public static FrontlineChunkState load(CompoundTag tag) {
        FrontlineChunkKey key = new FrontlineChunkKey(tag.getString("dimension"), tag.getInt("x"), tag.getInt("z"));
        return new FrontlineChunkState(key, tag.getString("owner"), tag.getString("captureTeam"), tag.getInt("progressTicks"));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
