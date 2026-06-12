package ru.liko.pjmbasemod.common.region;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

public final class Region {

    private final String name;
    private String displayName;
    private String dimension = "";
    private Integer pos1X;
    private Integer pos1Z;
    private Integer pos2X;
    private Integer pos2Z;
    private boolean frontline;

    public Region(String name) {
        this.name = name == null ? "region" : name.trim();
        this.displayName = this.name;
    }

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName == null || displayName.isBlank() ? name : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null || displayName.isBlank() ? name : displayName.trim();
    }

    public String dimension() {
        return dimension;
    }

    public boolean isFrontline() {
        return frontline;
    }

    public void setFrontline(boolean frontline) {
        this.frontline = frontline;
    }

    public void setPos1(String dimension, ChunkPos pos) {
        this.dimension = dimension == null ? "" : dimension;
        this.pos1X = pos.x;
        this.pos1Z = pos.z;
    }

    public void setPos2(String dimension, ChunkPos pos) {
        if (this.dimension.isBlank()) this.dimension = dimension == null ? "" : dimension;
        this.pos2X = pos.x;
        this.pos2Z = pos.z;
    }

    public boolean isComplete() {
        return !dimension.isBlank() && pos1X != null && pos1Z != null && pos2X != null && pos2Z != null;
    }

    public int minX() {
        return Math.min(pos1X == null ? 0 : pos1X, pos2X == null ? 0 : pos2X);
    }

    public int maxX() {
        return Math.max(pos1X == null ? 0 : pos1X, pos2X == null ? 0 : pos2X);
    }

    public int minZ() {
        return Math.min(pos1Z == null ? 0 : pos1Z, pos2Z == null ? 0 : pos2Z);
    }

    public int maxZ() {
        return Math.max(pos1Z == null ? 0 : pos1Z, pos2Z == null ? 0 : pos2Z);
    }

    public long chunkCount() {
        if (!isComplete()) return 0;
        return (long) (maxX() - minX() + 1) * (long) (maxZ() - minZ() + 1);
    }

    public boolean contains(String dimension, int chunkX, int chunkZ) {
        if (!isComplete() || !this.dimension.equals(dimension)) return false;
        return chunkX >= minX() && chunkX <= maxX() && chunkZ >= minZ() && chunkZ <= maxZ();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("displayName", displayName());
        tag.putString("dimension", dimension);
        tag.putBoolean("frontline", frontline);
        if (pos1X != null) tag.putInt("pos1X", pos1X);
        if (pos1Z != null) tag.putInt("pos1Z", pos1Z);
        if (pos2X != null) tag.putInt("pos2X", pos2X);
        if (pos2Z != null) tag.putInt("pos2Z", pos2Z);
        return tag;
    }

    public static Region load(CompoundTag tag) {
        Region region = new Region(tag.getString("name"));
        if (tag.contains("displayName", net.minecraft.nbt.Tag.TAG_STRING)) {
            region.setDisplayName(tag.getString("displayName"));
        }
        region.frontline = tag.getBoolean("frontline");
        region.dimension = tag.getString("dimension");
        if (tag.contains("pos1X", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) region.pos1X = tag.getInt("pos1X");
        if (tag.contains("pos1Z", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) region.pos1Z = tag.getInt("pos1Z");
        if (tag.contains("pos2X", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) region.pos2X = tag.getInt("pos2X");
        if (tag.contains("pos2Z", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) region.pos2Z = tag.getInt("pos2Z");
        return region;
    }
}
