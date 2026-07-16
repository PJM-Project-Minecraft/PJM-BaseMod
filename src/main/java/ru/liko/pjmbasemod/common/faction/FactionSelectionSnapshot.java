package ru.liko.pjmbasemod.common.faction;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record FactionSelectionSnapshot(
        List<TeamEntry> teams,
        String currentTeam,
        String currentRole,
        boolean required
) {

    /** {@code locked} — фракция «по приглашению», а у зрителя приглашения нет: выбрать нельзя. */
    public record TeamEntry(String id, String displayName, int color, boolean locked, List<RoleEntry> roles) {
    }

    public record RoleEntry(String id, String displayName, int color, int limit, int current) {
        public boolean unlimited() {
            return limit < 0;
        }

        public boolean disabled() {
            return limit == 0;
        }

        public boolean full() {
            return limit > 0 && current >= limit;
        }

        public boolean available() {
            return !disabled() && !full();
        }
    }

    public static void write(FriendlyByteBuf buf, FactionSelectionSnapshot snapshot) {
        buf.writeUtf(snapshot.currentTeam());
        buf.writeUtf(snapshot.currentRole());
        buf.writeBoolean(snapshot.required());
        buf.writeVarInt(snapshot.teams().size());
        for (TeamEntry team : snapshot.teams()) {
            buf.writeUtf(team.id());
            buf.writeUtf(team.displayName());
            buf.writeVarInt(team.color());
            buf.writeBoolean(team.locked());
            buf.writeVarInt(team.roles().size());
            for (RoleEntry role : team.roles()) {
                buf.writeUtf(role.id());
                buf.writeUtf(role.displayName());
                buf.writeVarInt(role.color());
                buf.writeInt(role.limit());
                buf.writeVarInt(role.current());
            }
        }
    }

    public static FactionSelectionSnapshot read(FriendlyByteBuf buf) {
        String currentTeam = buf.readUtf();
        String currentRole = buf.readUtf();
        boolean required = buf.readBoolean();
        int teamCount = buf.readVarInt();
        List<TeamEntry> teams = new ArrayList<>(teamCount);
        for (int i = 0; i < teamCount; i++) {
            String id = buf.readUtf();
            String displayName = buf.readUtf();
            int color = buf.readVarInt();
            boolean locked = buf.readBoolean();
            int roleCount = buf.readVarInt();
            List<RoleEntry> roles = new ArrayList<>(roleCount);
            for (int j = 0; j < roleCount; j++) {
                roles.add(new RoleEntry(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readInt(), buf.readVarInt()));
            }
            teams.add(new TeamEntry(id, displayName, color, locked, List.copyOf(roles)));
        }
        return new FactionSelectionSnapshot(List.copyOf(teams), currentTeam, currentRole, required);
    }
}
