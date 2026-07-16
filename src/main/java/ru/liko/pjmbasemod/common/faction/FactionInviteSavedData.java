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

    private final Map<String, Map<String, Long>> invitesByTeam = new LinkedHashMap<>();

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
            data.invitesByTeam.computeIfAbsent(team, k -> new LinkedHashMap<>())
                    .put(name, entry.getLong("expiresAt"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Map<String, Long>> team : invitesByTeam.entrySet()) {
            for (Map.Entry<String, Long> invite : team.getValue().entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("team", team.getKey());
                entry.putString("name", invite.getKey());
                entry.putLong("expiresAt", invite.getValue());
                list.add(entry);
            }
        }
        tag.put("invites", list);
        return tag;
    }

    /** Выдать приглашение. {@code ttlMinutes <= 0} — бессрочное. Повторный вызов обновляет срок. */
    public void invite(String teamId, String playerName, int ttlMinutes) {
        String team = Teams.normalize(teamId);
        String name = normalizeName(playerName);
        if (team.isBlank() || name.isBlank()) return;
        long expiresAt = ttlMinutes <= 0 ? 0L : System.currentTimeMillis() + ttlMinutes * 60_000L;
        invitesByTeam.computeIfAbsent(team, k -> new LinkedHashMap<>()).put(name, expiresAt);
        setDirty();
    }

    public boolean revoke(String teamId, String playerName) {
        Map<String, Long> team = invitesByTeam.get(Teams.normalize(teamId));
        if (team == null) return false;
        boolean removed = team.remove(normalizeName(playerName)) != null;
        if (removed) setDirty();
        return removed;
    }

    public boolean isInvited(String teamId, String playerName) {
        purgeExpired(Teams.normalize(teamId));
        Map<String, Long> team = invitesByTeam.get(Teams.normalize(teamId));
        return team != null && team.containsKey(normalizeName(playerName));
    }

    /** Активные приглашения фракции: ник → срок (millis, 0 = бессрочно). */
    public Map<String, Long> invites(String teamId) {
        purgeExpired(Teams.normalize(teamId));
        Map<String, Long> team = invitesByTeam.get(Teams.normalize(teamId));
        return team == null ? Map.of() : Map.copyOf(team);
    }

    /** Погасить приглашение после успешного вступления. */
    public void consume(String teamId, String playerName) {
        revoke(teamId, playerName);
    }

    private void purgeExpired(String team) {
        Map<String, Long> invites = invitesByTeam.get(team);
        if (invites == null) return;
        long now = System.currentTimeMillis();
        boolean changed = invites.values().removeIf(expiresAt -> expiresAt != 0L && expiresAt <= now);
        if (invites.isEmpty()) invitesByTeam.remove(team);
        if (changed) setDirty();
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
