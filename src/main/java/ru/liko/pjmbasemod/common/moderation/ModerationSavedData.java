package ru.liko.pjmbasemod.common.moderation;

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

/**
 * Персистентное состояние модерации (per-world SavedData). Хранит по игроку активный бан,
 * войс-мут, текст-мут, список активных варнов и аудит-историю; отдельно — IP-баны.
 * Время — эпоха в мс ({@link System#currentTimeMillis()}), чтобы наказания переживали рестарт
 * и действовали для оффлайн-игроков. Перманент = {@link DurationParser#PERMANENT}.
 */
public final class ModerationSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_moderation";
    /** Ограничение размера аудит-истории на игрока, чтобы NBT не рос бесконечно. */
    private static final int MAX_HISTORY = 200;
    /** Ограничение размера списка варнов на игрока (пороги эскалации малы). */
    private static final int MAX_WARNS = 200;

    private static final SavedData.Factory<ModerationSavedData> FACTORY = new SavedData.Factory<>(
            ModerationSavedData::new,
            ModerationSavedData::load
    );

    private final Map<UUID, ModerationProfile> profiles = new LinkedHashMap<>();
    private final Map<String, BanEntry> ipBans = new LinkedHashMap<>();

    public static ModerationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // ---------------------------------------------------------------- доступ / чтение

    @Nullable
    public ModerationProfile profile(UUID playerId) {
        return playerId == null ? null : profiles.get(playerId);
    }

    private ModerationProfile profileOrCreate(UUID playerId, String name) {
        ModerationProfile profile = profiles.computeIfAbsent(playerId, id -> new ModerationProfile(safeName(name)));
        profile.lastKnownName = safeName(name);
        return profile;
    }

    /** Неизменяемый снимок всех профилей (для сборки снапшота GUI/списков). */
    public Map<UUID, ModerationProfile> entries() {
        return Map.copyOf(profiles);
    }

    @Nullable
    public BanEntry activeBan(UUID playerId) {
        ModerationProfile p = profile(playerId);
        return p == null ? null : p.activeBan;
    }

    @Nullable
    public BanEntry ipBan(String ip) {
        return ip == null ? null : ipBans.get(ip);
    }

    public Map<String, BanEntry> ipBans() {
        return Map.copyOf(ipBans);
    }

    // ---------------------------------------------------------------- мутаторы (все setDirty)

    public void setBan(UUID playerId, String name, BanEntry ban) {
        if (playerId == null || ban == null) return;
        profileOrCreate(playerId, name).activeBan = ban;
        setDirty();
    }

    @Nullable
    public BanEntry clearBan(UUID playerId) {
        ModerationProfile p = profile(playerId);
        if (p == null || p.activeBan == null) return null;
        BanEntry removed = p.activeBan;
        p.activeBan = null;
        setDirty();
        return removed;
    }

    public void setVoiceMute(UUID playerId, String name, MuteEntry mute) {
        if (playerId == null || mute == null) return;
        profileOrCreate(playerId, name).voiceMute = mute;
        setDirty();
    }

    @Nullable
    public MuteEntry clearVoiceMute(UUID playerId) {
        ModerationProfile p = profile(playerId);
        if (p == null || p.voiceMute == null) return null;
        MuteEntry removed = p.voiceMute;
        p.voiceMute = null;
        setDirty();
        return removed;
    }

    public void setTextMute(UUID playerId, String name, MuteEntry mute) {
        if (playerId == null || mute == null) return;
        profileOrCreate(playerId, name).textMute = mute;
        setDirty();
    }

    @Nullable
    public MuteEntry clearTextMute(UUID playerId) {
        ModerationProfile p = profile(playerId);
        if (p == null || p.textMute == null) return null;
        MuteEntry removed = p.textMute;
        p.textMute = null;
        setDirty();
        return removed;
    }

    public void addWarn(UUID playerId, String name, WarnEntry warn) {
        if (playerId == null || warn == null) return;
        List<WarnEntry> warns = profileOrCreate(playerId, name).warns;
        warns.add(warn);
        // Кап, чтобы NBT не рос неограниченно при частых варнах (эскалация оперирует малыми порогами).
        while (warns.size() > MAX_WARNS) warns.remove(0);
        setDirty();
    }

    public int clearWarns(UUID playerId) {
        ModerationProfile p = profile(playerId);
        if (p == null || p.warns.isEmpty()) return 0;
        int count = p.warns.size();
        p.warns.clear();
        setDirty();
        return count;
    }

    public void addHistory(UUID playerId, String name, HistoryEntry entry) {
        if (playerId == null || entry == null) return;
        List<HistoryEntry> history = profileOrCreate(playerId, name).history;
        history.add(entry);
        while (history.size() > MAX_HISTORY) history.remove(0);
        setDirty();
    }

    public void setIpBan(String ip, BanEntry ban) {
        if (ip == null || ip.isBlank() || ban == null) return;
        ipBans.put(ip, ban);
        setDirty();
    }

    @Nullable
    public BanEntry clearIpBan(String ip) {
        if (ip == null) return null;
        BanEntry removed = ipBans.remove(ip);
        if (removed != null) setDirty();
        return removed;
    }

    public void touchName(UUID playerId, String name) {
        if (playerId == null) return;
        ModerationProfile p = profiles.get(playerId);
        if (p == null) return;
        String safe = safeName(name);
        if (!safe.equals(p.lastKnownName)) {
            p.lastKnownName = safe;
            setDirty();
        }
    }

    // ---------------------------------------------------------------- сериализация

    public static ModerationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ModerationSavedData data = new ModerationSavedData();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag pt = players.getCompound(i);
            try {
                UUID id = UUID.fromString(pt.getString("uuid"));
                ModerationProfile profile = new ModerationProfile(safeName(pt.getString("name")));
                if (pt.contains("ban", Tag.TAG_COMPOUND)) profile.activeBan = readBan(pt.getCompound("ban"));
                if (pt.contains("voiceMute", Tag.TAG_COMPOUND)) profile.voiceMute = readMute(pt.getCompound("voiceMute"));
                if (pt.contains("textMute", Tag.TAG_COMPOUND)) profile.textMute = readMute(pt.getCompound("textMute"));
                ListTag warns = pt.getList("warns", Tag.TAG_COMPOUND);
                for (int j = 0; j < warns.size(); j++) profile.warns.add(readWarn(warns.getCompound(j)));
                ListTag history = pt.getList("history", Tag.TAG_COMPOUND);
                for (int j = 0; j < history.size(); j++) {
                    HistoryEntry h = readHistory(history.getCompound(j));
                    if (h != null) profile.history.add(h);
                }
                data.profiles.put(id, profile);
            } catch (IllegalArgumentException ignored) {
            }
        }
        ListTag ipList = tag.getList("ipBans", Tag.TAG_COMPOUND);
        for (int i = 0; i < ipList.size(); i++) {
            CompoundTag it = ipList.getCompound(i);
            String ip = it.getString("ip");
            if (!ip.isBlank() && it.contains("ban", Tag.TAG_COMPOUND)) {
                data.ipBans.put(ip, readBan(it.getCompound("ban")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, ModerationProfile> e : profiles.entrySet()) {
            ModerationProfile p = e.getValue();
            CompoundTag pt = new CompoundTag();
            pt.putString("uuid", e.getKey().toString());
            pt.putString("name", p.lastKnownName);
            if (p.activeBan != null) pt.put("ban", writeBan(p.activeBan));
            if (p.voiceMute != null) pt.put("voiceMute", writeMute(p.voiceMute));
            if (p.textMute != null) pt.put("textMute", writeMute(p.textMute));
            ListTag warns = new ListTag();
            for (WarnEntry w : p.warns) warns.add(writeWarn(w));
            pt.put("warns", warns);
            ListTag history = new ListTag();
            for (HistoryEntry h : p.history) history.add(writeHistory(h));
            pt.put("history", history);
            players.add(pt);
        }
        tag.put("players", players);
        ListTag ipList = new ListTag();
        for (Map.Entry<String, BanEntry> e : ipBans.entrySet()) {
            CompoundTag it = new CompoundTag();
            it.putString("ip", e.getKey());
            it.put("ban", writeBan(e.getValue()));
            ipList.add(it);
        }
        tag.put("ipBans", ipList);
        return tag;
    }

    private static CompoundTag writeBan(BanEntry b) {
        CompoundTag t = new CompoundTag();
        t.putString("reason", b.reason());
        if (b.moderatorId() != null) t.putString("modId", b.moderatorId().toString());
        t.putString("modName", b.moderatorName());
        t.putLong("issued", b.issuedAtMs());
        t.putLong("expires", b.expiresAtMs());
        return t;
    }

    private static BanEntry readBan(CompoundTag t) {
        return new BanEntry(t.getString("reason"), parseUuid(t.getString("modId")),
                t.getString("modName"), t.getLong("issued"), t.getLong("expires"));
    }

    private static CompoundTag writeMute(MuteEntry m) {
        CompoundTag t = new CompoundTag();
        t.putString("reason", m.reason());
        if (m.moderatorId() != null) t.putString("modId", m.moderatorId().toString());
        t.putString("modName", m.moderatorName());
        t.putLong("issued", m.issuedAtMs());
        t.putLong("expires", m.expiresAtMs());
        return t;
    }

    private static MuteEntry readMute(CompoundTag t) {
        return new MuteEntry(t.getString("reason"), parseUuid(t.getString("modId")),
                t.getString("modName"), t.getLong("issued"), t.getLong("expires"));
    }

    private static CompoundTag writeWarn(WarnEntry w) {
        CompoundTag t = new CompoundTag();
        t.putString("reason", w.reason());
        if (w.moderatorId() != null) t.putString("modId", w.moderatorId().toString());
        t.putString("modName", w.moderatorName());
        t.putLong("issued", w.issuedAtMs());
        return t;
    }

    private static WarnEntry readWarn(CompoundTag t) {
        return new WarnEntry(t.getString("reason"), parseUuid(t.getString("modId")),
                t.getString("modName"), t.getLong("issued"));
    }

    private static CompoundTag writeHistory(HistoryEntry h) {
        CompoundTag t = new CompoundTag();
        t.putString("type", h.type().id());
        t.putString("action", h.action());
        t.putString("reason", h.reason());
        t.putString("modName", h.moderatorName());
        t.putLong("ts", h.timestampMs());
        t.putLong("duration", h.durationMs());
        return t;
    }

    @Nullable
    private static HistoryEntry readHistory(CompoundTag t) {
        PunishmentType type = PunishmentType.byId(t.getString("type"));
        if (type == null) return null;
        return new HistoryEntry(type, t.getString("action"), t.getString("reason"),
                t.getString("modName"), t.getLong("ts"), t.getLong("duration"));
    }

    @Nullable
    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "unknown" : name.trim();
    }

    // ---------------------------------------------------------------- модель данных

    /** Изменяемый профиль игрока. Мутируется только через методы {@link ModerationSavedData}. */
    public static final class ModerationProfile {
        private String lastKnownName;
        @Nullable private BanEntry activeBan;
        @Nullable private MuteEntry voiceMute;
        @Nullable private MuteEntry textMute;
        private final List<WarnEntry> warns = new ArrayList<>();
        private final List<HistoryEntry> history = new ArrayList<>();

        ModerationProfile(String lastKnownName) {
            this.lastKnownName = lastKnownName;
        }

        public String lastKnownName()      { return lastKnownName; }
        @Nullable public BanEntry activeBan()  { return activeBan; }
        @Nullable public MuteEntry voiceMute() { return voiceMute; }
        @Nullable public MuteEntry textMute()  { return textMute; }
        public List<WarnEntry> warns()     { return List.copyOf(warns); }
        public List<HistoryEntry> history() { return List.copyOf(history); }
        public int warnCount()             { return warns.size(); }
    }

    public record BanEntry(String reason, @Nullable UUID moderatorId, String moderatorName,
                           long issuedAtMs, long expiresAtMs) {
        public boolean isPermanent() { return expiresAtMs == DurationParser.PERMANENT; }
    }

    public record MuteEntry(String reason, @Nullable UUID moderatorId, String moderatorName,
                            long issuedAtMs, long expiresAtMs) {
        public boolean isPermanent() { return expiresAtMs == DurationParser.PERMANENT; }
    }

    public record WarnEntry(String reason, @Nullable UUID moderatorId, String moderatorName, long issuedAtMs) {}

    public record HistoryEntry(PunishmentType type, String action, String reason,
                               String moderatorName, long timestampMs, long durationMs) {}
}
