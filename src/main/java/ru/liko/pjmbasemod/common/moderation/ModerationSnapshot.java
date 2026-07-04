package ru.liko.pjmbasemod.common.moderation;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Снимок состояния модерации для GUI: список игроков (онлайн + известные из SavedData)
 * с их статусами наказаний. Живёт в common, чтобы использоваться и сервером, и клиентом.
 */
public record ModerationSnapshot(List<PlayerModEntry> players) {

    /** Строка игрока в списке модерации. */
    public record PlayerModEntry(UUID id, String name, boolean online, boolean banned,
                                 boolean voiceMuted, boolean textMuted, int warnCount,
                                 long banExpiresMs, String banReason) {}

    public static void write(FriendlyByteBuf buf, ModerationSnapshot snapshot) {
        buf.writeVarInt(snapshot.players().size());
        for (PlayerModEntry e : snapshot.players()) {
            buf.writeUUID(e.id());
            buf.writeUtf(e.name());
            buf.writeBoolean(e.online());
            buf.writeBoolean(e.banned());
            buf.writeBoolean(e.voiceMuted());
            buf.writeBoolean(e.textMuted());
            buf.writeVarInt(e.warnCount());
            buf.writeLong(e.banExpiresMs());
            buf.writeUtf(e.banReason());
        }
    }

    public static ModerationSnapshot read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<PlayerModEntry> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf();
            boolean online = buf.readBoolean();
            boolean banned = buf.readBoolean();
            boolean voiceMuted = buf.readBoolean();
            boolean textMuted = buf.readBoolean();
            int warnCount = buf.readVarInt();
            long banExpiresMs = buf.readLong();
            String banReason = buf.readUtf();
            players.add(new PlayerModEntry(id, name, online, banned, voiceMuted, textMuted,
                    warnCount, banExpiresMs, banReason));
        }
        return new ModerationSnapshot(List.copyOf(players));
    }
}
