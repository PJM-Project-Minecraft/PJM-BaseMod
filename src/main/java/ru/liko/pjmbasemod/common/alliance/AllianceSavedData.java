package ru.liko.pjmbasemod.common.alliance;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.liko.pjmbasemod.common.teams.Teams;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Союзы фракций: строго <b>пара</b> — одна фракция состоит максимум в одном союзе.
 * Хранится как список пар; в памяти индексируется по обеим сторонам, поэтому
 * {@link #allyOf(String)} — O(1) и симметричен.
 *
 * <p>Срок — wall-clock millis, {@code 0} = бессрочно («навсегда»). Просроченные союзы
 * и предложения вычищаются лениво при обращении; уведомление об истечении шлёт
 * {@link Alliances#onServerTick(MinecraftServer)}.</p>
 */
public final class AllianceSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_alliances";
    private static final SavedData.Factory<AllianceSavedData> FACTORY = new SavedData.Factory<>(
            AllianceSavedData::new,
            AllianceSavedData::load
    );

    /** Действующий союз. {@code expiresAt == 0} — бессрочный. */
    public record Alliance(String teamA, String teamB, long expiresAt) {
        public String other(String teamId) {
            return teamA.equals(teamId) ? teamB : teamA;
        }
    }

    /** Предложение союза: кто, кому, на сколько (0 = навсегда) и до какого времени живёт само предложение. */
    public record Offer(String from, String to, long durationMinutes, long offerExpiresAt) {}

    /** teamId → союз (одна и та же запись лежит под обоими ключами). */
    private final Map<String, Alliance> byTeam = new LinkedHashMap<>();
    /** teamId предлагающего → предложение. Одна фракция держит максимум одно живое предложение. */
    private final Map<String, Offer> offers = new LinkedHashMap<>();

    public static AllianceSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static AllianceSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AllianceSavedData data = new AllianceSavedData();
        ListTag list = tag.getList("alliances", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String a = Teams.normalize(entry.getString("a"));
            String b = Teams.normalize(entry.getString("b"));
            if (a.isBlank() || b.isBlank() || a.equals(b)) continue;
            data.index(new Alliance(a, b, entry.getLong("expiresAt")));
        }
        ListTag offerList = tag.getList("offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < offerList.size(); i++) {
            CompoundTag entry = offerList.getCompound(i);
            String from = Teams.normalize(entry.getString("from"));
            String to = Teams.normalize(entry.getString("to"));
            if (from.isBlank() || to.isBlank()) continue;
            data.offers.put(from, new Offer(from, to, entry.getLong("duration"), entry.getLong("offerExpiresAt")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Alliance alliance : alliances()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("a", alliance.teamA());
            entry.putString("b", alliance.teamB());
            entry.putLong("expiresAt", alliance.expiresAt());
            list.add(entry);
        }
        tag.put("alliances", list);

        ListTag offerList = new ListTag();
        for (Offer offer : offers.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("from", offer.from());
            entry.putString("to", offer.to());
            entry.putLong("duration", offer.durationMinutes());
            entry.putLong("offerExpiresAt", offer.offerExpiresAt());
            offerList.add(entry);
        }
        tag.put("offers", offerList);
        return tag;
    }

    /** Уникальные союзы (каждая пара один раз). */
    public List<Alliance> alliances() {
        purgeExpired();
        List<Alliance> result = new ArrayList<>();
        for (Map.Entry<String, Alliance> entry : byTeam.entrySet()) {
            // Дедуп: пара лежит под двумя ключами, берём только со стороны teamA.
            if (entry.getKey().equals(entry.getValue().teamA())) result.add(entry.getValue());
        }
        return result;
    }

    @Nullable
    public Alliance allianceOf(String teamId) {
        purgeExpired();
        return byTeam.get(Teams.normalize(teamId));
    }

    /** Союзная фракция или {@code null}, если союза нет. */
    @Nullable
    public String allyOf(String teamId) {
        Alliance alliance = allianceOf(teamId);
        return alliance == null ? null : alliance.other(Teams.normalize(teamId));
    }

    /**
     * Заключить союз. {@code durationMinutes <= 0} — бессрочный.
     * Возвращает {@code false}, если любая из сторон уже состоит в союзе (лимит — пара).
     */
    public boolean form(String teamA, String teamB, long durationMinutes) {
        String a = Teams.normalize(teamA);
        String b = Teams.normalize(teamB);
        if (a.isBlank() || b.isBlank() || a.equals(b)) return false;
        if (allianceOf(a) != null || allianceOf(b) != null) return false;
        long expiresAt = durationMinutes <= 0 ? 0L : System.currentTimeMillis() + durationMinutes * 60_000L;
        index(new Alliance(a, b, expiresAt));
        offers.remove(a);
        offers.remove(b);
        setDirty();
        return true;
    }

    /** Разрыв союза с любой стороны. Возвращает бывшего союзника или {@code null}. */
    @Nullable
    public String dissolve(String teamId) {
        String team = Teams.normalize(teamId);
        Alliance alliance = byTeam.remove(team);
        if (alliance == null) return null;
        String other = alliance.other(team);
        byTeam.remove(other);
        setDirty();
        return other;
    }

    public void offer(String from, String to, long durationMinutes, int offerTtlMinutes) {
        String a = Teams.normalize(from);
        String b = Teams.normalize(to);
        if (a.isBlank() || b.isBlank() || a.equals(b)) return;
        offers.put(a, new Offer(a, b, Math.max(0, durationMinutes),
                System.currentTimeMillis() + Math.max(1, offerTtlMinutes) * 60_000L));
        setDirty();
    }

    /** Живое предложение, адресованное указанной фракции. */
    @Nullable
    public Offer offerTo(String teamId) {
        purgeExpired();
        String team = Teams.normalize(teamId);
        for (Offer offer : offers.values()) {
            if (offer.to().equals(team)) return offer;
        }
        return null;
    }

    @Nullable
    public Offer offerFrom(String teamId) {
        purgeExpired();
        return offers.get(Teams.normalize(teamId));
    }

    public boolean revokeOffer(String from) {
        boolean removed = offers.remove(Teams.normalize(from)) != null;
        if (removed) setDirty();
        return removed;
    }

    /** Сброс всех союзов и предложений — вайп сезона. */
    public void clearAll() {
        if (byTeam.isEmpty() && offers.isEmpty()) return;
        byTeam.clear();
        offers.clear();
        setDirty();
    }

    /**
     * Снимает истёкшие союзы и предложения. Возвращает союзы, которые истекли именно
     * сейчас — вызывающий (тик) рассылает по ним уведомление.
     */
    public List<Alliance> purgeExpired() {
        long now = System.currentTimeMillis();
        List<Alliance> expired = new ArrayList<>();
        for (Map.Entry<String, Alliance> entry : List.copyOf(byTeam.entrySet())) {
            Alliance alliance = entry.getValue();
            if (alliance.expiresAt() == 0L || alliance.expiresAt() > now) continue;
            if (byTeam.remove(entry.getKey()) != null && entry.getKey().equals(alliance.teamA())) {
                expired.add(alliance);
            }
        }
        boolean offersChanged = offers.values().removeIf(offer -> offer.offerExpiresAt() <= now);
        if (!expired.isEmpty() || offersChanged) setDirty();
        return expired;
    }

    private void index(Alliance alliance) {
        byTeam.put(alliance.teamA(), alliance);
        byTeam.put(alliance.teamB(), alliance);
    }
}
