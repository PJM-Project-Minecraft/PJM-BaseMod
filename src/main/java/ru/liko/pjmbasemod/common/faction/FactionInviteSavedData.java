package ru.liko.pjmbasemod.common.faction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import ru.liko.pjmbasemod.common.teams.Teams;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Приглашения в закрытые («по приглашению») фракции: teamId → (ник в lowercase → срок действия).
 * Ключ — ник, а не UUID: приглашённый мог ещё ни разу не заходить на сервер, а членство в
 * scoreboard-командах и так хранится по имени. Срок — wall-clock millis; {@code 0} = бессрочно.
 * Просроченные записи вычищаются лениво при любом обращении.
 */
public final class FactionInviteSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_faction_invites";
    private static final SavedData.Factory<FactionInviteSavedData> FACTORY = new SavedData.Factory<>(
            FactionInviteSavedData::new,
            FactionInviteSavedData::load
    );

    /** Одно приглашение: срок действия (millis, 0 = бессрочно) и ник выдавшего. */
    public record Invite(long expiresAt, String inviter) {}

    private final Map<String, Map<String, Invite>> invitesByTeam = new LinkedHashMap<>();

    public static FactionInviteSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static FactionInviteSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionInviteSavedData data = new FactionInviteSavedData();
        ListTag list = tag.getList("invites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String team = Teams.normalize(entry.getString("team"));
            String name = normalizeName(entry.getString("name"));
            if (team.isBlank() || name.isBlank()) continue;
            // inviter появился позже: в старых сохранениях ключа нет — читается как "".
            data.invitesByTeam.computeIfAbsent(team, k -> new LinkedHashMap<>())
                    .put(name, new Invite(entry.getLong("expiresAt"), entry.getString("inviter")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Map<String, Invite>> team : invitesByTeam.entrySet()) {
            for (Map.Entry<String, Invite> invite : team.getValue().entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("team", team.getKey());
                entry.putString("name", invite.getKey());
                entry.putLong("expiresAt", invite.getValue().expiresAt());
                entry.putString("inviter", invite.getValue().inviter());
                list.add(entry);
            }
        }
        tag.put("invites", list);
        return tag;
    }

    /** Выдать приглашение. {@code ttlMinutes <= 0} — бессрочное. Повторный вызов обновляет срок. */
    public void invite(String teamId, String playerName, int ttlMinutes, String inviterName) {
        String team = Teams.normalize(teamId);
        String name = normalizeName(playerName);
        if (team.isBlank() || name.isBlank()) return;
        long expiresAt = ttlMinutes <= 0 ? 0L : System.currentTimeMillis() + ttlMinutes * 60_000L;
        invitesByTeam.computeIfAbsent(team, k -> new LinkedHashMap<>())
                .put(name, new Invite(expiresAt, inviterName == null ? "" : inviterName));
        setDirty();
    }

    public boolean revoke(String teamId, String playerName) {
        Map<String, Invite> team = invitesByTeam.get(Teams.normalize(teamId));
        if (team == null) return false;
        boolean removed = team.remove(normalizeName(playerName)) != null;
        if (removed) setDirty();
        return removed;
    }

    public boolean isInvited(String teamId, String playerName) {
        purgeExpired(Teams.normalize(teamId));
        Map<String, Invite> team = invitesByTeam.get(Teams.normalize(teamId));
        return team != null && team.containsKey(normalizeName(playerName));
    }

    /** Активные приглашения фракции: ник → приглашение. */
    public Map<String, Invite> invites(String teamId) {
        purgeExpired(Teams.normalize(teamId));
        Map<String, Invite> team = invitesByTeam.get(Teams.normalize(teamId));
        return team == null ? Map.of() : Map.copyOf(team);
    }

    /** Погасить приглашение после успешного вступления. */
    public void consume(String teamId, String playerName) {
        revoke(teamId, playerName);
    }

    /**
     * Фракции, пригласившие игрока (обратный поиск по нику). Нужен при входе:
     * приглашение могли выдать, пока игрок был оффлайн, и предложить его надо на логине.
     */
    public java.util.List<String> teamsInviting(String playerName) {
        String name = normalizeName(playerName);
        if (name.isBlank()) return java.util.List.of();
        java.util.List<String> teams = new java.util.ArrayList<>();
        for (String team : java.util.List.copyOf(invitesByTeam.keySet())) {
            purgeExpired(team);
            Map<String, Invite> invites = invitesByTeam.get(team);
            if (invites != null && invites.containsKey(name)) teams.add(team);
        }
        return teams;
    }

    /** Конкретное приглашение или {@code null}, если его нет. */
    @javax.annotation.Nullable
    public Invite inviteOf(String teamId, String playerName) {
        Map<String, Invite> team = invitesByTeam.get(Teams.normalize(teamId));
        return team == null ? null : team.get(normalizeName(playerName));
    }

    private void purgeExpired(String team) {
        Map<String, Invite> invites = invitesByTeam.get(team);
        if (invites == null) return;
        long now = System.currentTimeMillis();
        boolean changed = invites.values().removeIf(inv -> inv.expiresAt() != 0L && inv.expiresAt() <= now);
        if (invites.isEmpty()) invitesByTeam.remove(team);
        if (changed) setDirty();
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
