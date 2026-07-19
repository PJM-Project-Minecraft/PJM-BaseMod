package ru.liko.pjmbasemod.common.capturepoint;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.liko.pjmbasemod.common.teams.Teams;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-world хранилище точек захвата: геометрия (OP-разметка) + runtime-состояние
 * (владелец/прогресс). Сохраняется в NBT через ванильный {@link SavedData}.
 */
public final class CapturePointSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_capturepoints";
    private static final SavedData.Factory<CapturePointSavedData> FACTORY = new SavedData.Factory<>(
            CapturePointSavedData::new,
            CapturePointSavedData::load
    );

    private final Map<String, Entry> points = new LinkedHashMap<>();

    public static CapturePointSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static CapturePointSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CapturePointSavedData data = new CapturePointSavedData();
        ListTag list = tag.getList("points", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Entry entry = Entry.load(list.getCompound(i));
            if (entry != null && !entry.id.isBlank()) data.points.put(key(entry.id), entry);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : points.values()) list.add(entry.save());
        tag.put("points", list);
        return tag;
    }

    public Collection<Entry> entries() {
        return points.values();
    }

    @Nullable
    public Entry entry(String id) {
        if (id == null) return null;
        return points.get(key(id));
    }

    /** Команда-владелец точки, содержащей блок (x,z) в измерении; "" — нет точки/нейтрально. */
    public String ownerTeamAt(String dimension, int x, int z) {
        for (Entry e : points.values()) {
            if (!e.dimension.equals(dimension) || e.vertices.size() < 3) continue;
            if (CapturePoint.contains(e.vertices, x, z)) return e.ownerTeamId == null ? "" : e.ownerTeamId;
        }
        return "";
    }

    /** Снапшоты всех точек для sync-пакета. */
    public List<CapturePoint> snapshots(int requiredTicks, @Nullable Map<String, Integer> contestedFlags) {
        List<CapturePoint> result = new ArrayList<>();
        for (Entry entry : points.values()) {
            boolean contested = contestedFlags != null && contestedFlags.getOrDefault(entry.id, 0) >= 2;
            result.add(entry.snapshot(requiredTicks, contested));
        }
        return List.copyOf(result);
    }

    public boolean addPoint(String id, String displayName, String dimension) {
        String key = key(id);
        if (points.containsKey(key)) return false;
        Entry entry = new Entry();
        entry.id = id;
        entry.displayName = displayName == null ? id : displayName;
        entry.dimension = dimension == null ? "minecraft:overworld" : dimension;
        entry.vertices = List.of();
        points.put(key, entry);
        setDirty();
        return true;
    }

    public boolean removePoint(String id) {
        if (id == null) return false;
        Entry removed = points.remove(key(id));
        if (removed == null) return false;
        setDirty();
        return true;
    }

    public boolean updateVertices(String id, List<CapturePoint.Vertex> vertices) {
        Entry entry = entry(id);
        if (entry == null) return false;
        entry.vertices = List.copyOf(vertices);
        setDirty();
        return true;
    }

    public boolean updateDisplayName(String id, String displayName) {
        Entry entry = entry(id);
        if (entry == null) return false;
        entry.displayName = displayName == null ? id : displayName;
        setDirty();
        return true;
    }

    public boolean setOwner(String id, String ownerTeamId) {
        Entry entry = entry(id);
        if (entry == null) return false;
        entry.ownerTeamId = ownerTeamId == null ? "" : Teams.normalize(ownerTeamId);
        entry.ownerColor = entry.ownerTeamId.isEmpty() ? 0x9B9B9B : Teams.color(null, entry.ownerTeamId);
        entry.captureTeamId = "";
        entry.progressTicks = entry.ownerTeamId.isEmpty() ? 0 : Integer.MAX_VALUE;
        setDirty();
        return true;
    }

    public boolean setOrder(String id, int order) {
        Entry entry = entry(id);
        if (entry == null) return false;
        entry.order = order;
        setDirty();
        return true;
    }

    public void clearAll() {
        if (points.isEmpty()) return;
        points.clear();
        setDirty();
    }

    /**
     * Сброс к новому сезону: все точки нейтральные, прогресс в ноль. Базовые точки —
     * крайние по order в каждом измерении — сохраняют владельца с полным контролем:
     * при {@code sequential=true} полностью нейтральная карта заблокировала бы захват
     * (гейт цепочки требует владения соседней точкой), а базы — админ-разметка.
     */
    public void resetForNewSeason() {
        Map<String, Entry> minByDim = new LinkedHashMap<>();
        Map<String, Entry> maxByDim = new LinkedHashMap<>();
        for (Entry e : points.values()) {
            minByDim.merge(e.dimension, e, (a, b) -> b.order < a.order ? b : a);
            maxByDim.merge(e.dimension, e, (a, b) -> b.order > a.order ? b : a);
        }
        for (Entry e : points.values()) {
            boolean base = (e == minByDim.get(e.dimension) || e == maxByDim.get(e.dimension))
                    && !e.ownerTeamId.isEmpty();
            e.captureTeamId = "";
            if (base) {
                e.progressTicks = Integer.MAX_VALUE; // полный контроль, как после setowner
            } else {
                e.ownerTeamId = "";
                e.ownerColor = 0x9B9B9B;
                e.progressTicks = 0;
            }
        }
        setDirty();
    }

    private static String key(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    /**
     * Мутабельная запись точки: геометрия + runtime-состояние.
     * Доступ из {@link CapturePointManager}.
     */
    public static final class Entry {
        public String id;
        public String displayName;
        public String dimension;
        public List<CapturePoint.Vertex> vertices = List.of();
        public String ownerTeamId = "";
        public int ownerColor = 0x9B9B9B;
        public String captureTeamId = "";
        public int progressTicks = 0;
        /** Порядок на линии фронта (для последовательного захвата). Соседи по order — цепочка. */
        public int order = 0;

        CapturePoint snapshot(int requiredTicks, boolean contested) {
            int percent;
            if (requiredTicks <= 0) {
                percent = ownerTeamId.isEmpty() ? 0 : 100;
            } else if (ownerTeamId.isEmpty()) {
                percent = Math.max(0, Math.min(100, progressTicks * 100 / requiredTicks));
            } else if (captureTeamId.isEmpty()) {
                percent = 100;
            } else {
                percent = Math.max(0, Math.min(100, progressTicks * 100 / requiredTicks));
            }
            return new CapturePoint(id, displayName, dimension,
                    List.copyOf(vertices), ownerTeamId, ownerColor, captureTeamId, percent, contested, order);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id);
            tag.putString("displayName", displayName);
            tag.putString("dimension", dimension);
            tag.putString("owner", ownerTeamId);
            tag.putInt("ownerColor", ownerColor);
            tag.putString("capture", captureTeamId);
            tag.putInt("progress", progressTicks);
            tag.putInt("order", order);
            ListTag vlist = new ListTag();
            for (CapturePoint.Vertex v : vertices) {
                CompoundTag vt = new CompoundTag();
                vt.putInt("x", v.x());
                vt.putInt("z", v.z());
                vlist.add(vt);
            }
            tag.put("vertices", vlist);
            return tag;
        }

        @Nullable
        static Entry load(CompoundTag tag) {
            Entry entry = new Entry();
            entry.id = tag.getString("id");
            entry.displayName = tag.getString("displayName");
            entry.dimension = tag.getString("dimension");
            entry.ownerTeamId = tag.getString("owner");
            entry.ownerColor = tag.getInt("ownerColor");
            entry.captureTeamId = tag.getString("capture");
            entry.progressTicks = Math.max(0, tag.getInt("progress"));
            entry.order = tag.getInt("order");
            ListTag vlist = tag.getList("vertices", Tag.TAG_COMPOUND);
            List<CapturePoint.Vertex> vertices = new ArrayList<>();
            for (int i = 0; i < vlist.size(); i++) {
                CompoundTag vt = vlist.getCompound(i);
                vertices.add(new CapturePoint.Vertex(vt.getInt("x"), vt.getInt("z")));
            }
            entry.vertices = List.copyOf(vertices);
            return entry;
        }
    }
}
