package ru.liko.pjmbasemod.common.basezone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Зона базы: блочный AABB (с высотой Y) в одной размерности, привязанный к команде-владельцу. */
public final class BaseZone {

    private final String name;
    private String displayName;
    private String dimension = "";
    private String owner = "";
    private Integer pos1X, pos1Y, pos1Z;
    private Integer pos2X, pos2Y, pos2Z;

    public BaseZone(String name) {
        this.name = name == null ? "basezone" : name.trim();
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

    public String owner() {
        return owner == null ? "" : owner;
    }

    public void setOwner(String owner) {
        this.owner = owner == null ? "" : owner.trim();
    }

    public void setPos1(String dimension, BlockPos pos) {
        this.dimension = dimension == null ? "" : dimension;
        this.pos1X = pos.getX();
        this.pos1Y = pos.getY();
        this.pos1Z = pos.getZ();
    }

    public void setPos2(String dimension, BlockPos pos) {
        if (this.dimension.isBlank()) this.dimension = dimension == null ? "" : dimension;
        this.pos2X = pos.getX();
        this.pos2Y = pos.getY();
        this.pos2Z = pos.getZ();
    }

    public boolean isComplete() {
        return !dimension.isBlank()
                && pos1X != null && pos1Y != null && pos1Z != null
                && pos2X != null && pos2Y != null && pos2Z != null;
    }

    public int minX() { return Math.min(nz(pos1X), nz(pos2X)); }
    public int maxX() { return Math.max(nz(pos1X), nz(pos2X)); }
    public int minY() { return Math.min(nz(pos1Y), nz(pos2Y)); }
    public int maxY() { return Math.max(nz(pos1Y), nz(pos2Y)); }
    public int minZ() { return Math.min(nz(pos1Z), nz(pos2Z)); }
    public int maxZ() { return Math.max(nz(pos1Z), nz(pos2Z)); }

    private static int nz(Integer v) { return v == null ? 0 : v; }

    public boolean contains(String dimension, BlockPos pos) {
        if (!isComplete() || !this.dimension.equals(dimension)) return false;
        return pos.getX() >= minX() && pos.getX() <= maxX()
                && pos.getY() >= minY() && pos.getY() <= maxY()
                && pos.getZ() >= minZ() && pos.getZ() <= maxZ();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("displayName", displayName());
        tag.putString("dimension", dimension);
        tag.putString("owner", owner());
        if (pos1X != null) tag.putInt("pos1X", pos1X);
        if (pos1Y != null) tag.putInt("pos1Y", pos1Y);
        if (pos1Z != null) tag.putInt("pos1Z", pos1Z);
        if (pos2X != null) tag.putInt("pos2X", pos2X);
        if (pos2Y != null) tag.putInt("pos2Y", pos2Y);
        if (pos2Z != null) tag.putInt("pos2Z", pos2Z);
        return tag;
    }

    public static BaseZone load(CompoundTag tag) {
        BaseZone zone = new BaseZone(tag.getString("name"));
        if (tag.contains("displayName", Tag.TAG_STRING)) zone.setDisplayName(tag.getString("displayName"));
        zone.dimension = tag.getString("dimension");
        zone.owner = tag.getString("owner");
        if (tag.contains("pos1X", Tag.TAG_ANY_NUMERIC)) zone.pos1X = tag.getInt("pos1X");
        if (tag.contains("pos1Y", Tag.TAG_ANY_NUMERIC)) zone.pos1Y = tag.getInt("pos1Y");
        if (tag.contains("pos1Z", Tag.TAG_ANY_NUMERIC)) zone.pos1Z = tag.getInt("pos1Z");
        if (tag.contains("pos2X", Tag.TAG_ANY_NUMERIC)) zone.pos2X = tag.getInt("pos2X");
        if (tag.contains("pos2Y", Tag.TAG_ANY_NUMERIC)) zone.pos2Y = tag.getInt("pos2Y");
        if (tag.contains("pos2Z", Tag.TAG_ANY_NUMERIC)) zone.pos2Z = tag.getInt("pos2Z");
        return zone;
    }
}
