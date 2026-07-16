package ru.liko.pjmbasemod.common.report;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Обращения игроков в администрацию (переписки), per-world. */
public final class ReportSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_reports";
    private static final SavedData.Factory<ReportSavedData> FACTORY = new SavedData.Factory<>(
            ReportSavedData::new,
            ReportSavedData::load
    );

    private final Map<Integer, Report> reports = new LinkedHashMap<>();
    private int nextId = 1;

    public static ReportSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static ReportSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ReportSavedData data = new ReportSavedData();
        data.nextId = Math.max(1, tag.getInt("nextId"));
        ListTag list = tag.getList("reports", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ReportCategory category;
            try {
                category = ReportCategory.valueOf(t.getString("category"));
            } catch (IllegalArgumentException ex) {
                category = ReportCategory.OTHER;
            }
            List<ReportMessage> messages = new ArrayList<>();
            ListTag msgs = t.getList("messages", Tag.TAG_COMPOUND);
            for (int j = 0; j < msgs.size(); j++) {
                CompoundTag m = msgs.getCompound(j);
                messages.add(new ReportMessage(m.getBoolean("admin"), m.getString("sender"),
                        m.getString("text"), m.getLong("time")));
            }
            Report r = new Report(
                    t.getInt("id"),
                    t.getUUID("reporterId"),
                    t.getString("reporterName"),
                    category,
                    t.getLong("createdAt"),
                    t.getBoolean("open"),
                    messages);
            data.reports.put(r.id(), r);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("nextId", nextId);
        ListTag list = new ListTag();
        for (Report r : reports.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", r.id());
            t.putUUID("reporterId", r.reporterId());
            t.putString("reporterName", r.reporterName());
            t.putString("category", r.category().name());
            t.putLong("createdAt", r.createdAt());
            t.putBoolean("open", r.open());
            ListTag msgs = new ListTag();
            for (ReportMessage m : r.messages()) {
                CompoundTag mt = new CompoundTag();
                mt.putBoolean("admin", m.fromAdmin());
                mt.putString("sender", m.senderName());
                mt.putString("text", m.text());
                mt.putLong("time", m.time());
                msgs.add(mt);
            }
            t.put("messages", msgs);
            list.add(t);
        }
        tag.put("reports", list);
        return tag;
    }

    /** Создать новое обращение с первым сообщением игрока. */
    public Report create(UUID reporterId, String reporterName, ReportCategory category, String firstMessage, long now) {
        int id = nextId++;
        List<ReportMessage> messages = new ArrayList<>();
        messages.add(new ReportMessage(false, reporterName, firstMessage, now));
        Report r = new Report(id, reporterId, reporterName, category, now, true, messages);
        reports.put(id, r);
        setDirty();
        return r;
    }

    /** Добавить сообщение в обращение. Возвращает обновлённое обращение или null. */
    @Nullable
    public Report addMessage(int id, boolean fromAdmin, String senderName, String text, long now) {
        Report r = reports.get(id);
        if (r == null) return null;
        r.messages().add(new ReportMessage(fromAdmin, senderName, text, now));
        setDirty();
        return r;
    }

    /** Активное (открытое) обращение игрока или null. */
    @Nullable
    public Report openReportOf(UUID reporterId) {
        for (Report r : reports.values()) {
            if (r.open() && r.reporterId().equals(reporterId)) return r;
        }
        return null;
    }

    @Nullable
    public Report find(int id) {
        return reports.get(id);
    }

    /** Пометить обращение закрытым. Возвращает true, если было открыто. */
    public boolean close(int id) {
        Report r = reports.get(id);
        if (r == null || !r.open()) return false;
        reports.put(id, r.closed());
        setDirty();
        return true;
    }

    /** Все обращения: открытые сверху, внутри — новые сверху. */
    public List<Report> all() {
        List<Report> out = new ArrayList<>(reports.values());
        out.sort((a, b) -> {
            if (a.open() != b.open()) return a.open() ? -1 : 1;
            return Integer.compare(b.id(), a.id());
        });
        return out;
    }

    /** Обращение. {@code messages} — мутабельный список (дополняется через {@link #addMessage}). */
    public record Report(int id, UUID reporterId, String reporterName, ReportCategory category,
                         long createdAt, boolean open, List<ReportMessage> messages) {
        public Report closed() {
            return new Report(id, reporterId, reporterName, category, createdAt, false, messages);
        }
    }
}
