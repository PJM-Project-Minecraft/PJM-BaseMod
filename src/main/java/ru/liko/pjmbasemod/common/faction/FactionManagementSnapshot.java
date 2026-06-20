package ru.liko.pjmbasemod.common.faction;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FactionManagementSnapshot(
        String teamId,
        String teamName,
        int teamColor,
        boolean canManage,
        List<MemberEntry> members,
        List<FactionSelectionSnapshot.RoleEntry> roles,
        boolean viewerCanAssignRoles,
        boolean viewerCanManageDeputies,
        boolean viewerCanSetOrder,
        int maxDeputies,
        int deputyCount,
        String orderText,
        String orderAuthor,
        int orderSecondsRemaining
) {

    public record MemberEntry(UUID playerId, String name, String roleId, boolean commander,
                              boolean deputy, int deputyPerms) {
    }

    public static void write(FriendlyByteBuf buf, FactionManagementSnapshot snapshot) {
        buf.writeUtf(snapshot.teamId());
        buf.writeUtf(snapshot.teamName());
        buf.writeVarInt(snapshot.teamColor());
        buf.writeBoolean(snapshot.canManage());

        buf.writeVarInt(snapshot.members().size());
        for (MemberEntry member : snapshot.members()) {
            buf.writeUUID(member.playerId());
            buf.writeUtf(member.name());
            buf.writeUtf(member.roleId());
            buf.writeBoolean(member.commander());
            buf.writeBoolean(member.deputy());
            buf.writeVarInt(member.deputyPerms());
        }

        buf.writeVarInt(snapshot.roles().size());
        for (FactionSelectionSnapshot.RoleEntry role : snapshot.roles()) {
            buf.writeUtf(role.id());
            buf.writeUtf(role.displayName());
            buf.writeVarInt(role.color());
            buf.writeInt(role.limit());
            buf.writeVarInt(role.current());
        }

        buf.writeBoolean(snapshot.viewerCanAssignRoles());
        buf.writeBoolean(snapshot.viewerCanManageDeputies());
        buf.writeBoolean(snapshot.viewerCanSetOrder());
        buf.writeVarInt(snapshot.maxDeputies());
        buf.writeVarInt(snapshot.deputyCount());
        buf.writeUtf(snapshot.orderText());
        buf.writeUtf(snapshot.orderAuthor());
        buf.writeInt(snapshot.orderSecondsRemaining());
    }

    public static FactionManagementSnapshot read(FriendlyByteBuf buf) {
        String teamId = buf.readUtf();
        String teamName = buf.readUtf();
        int teamColor = buf.readVarInt();
        boolean canManage = buf.readBoolean();

        int memberCount = buf.readVarInt();
        List<MemberEntry> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            members.add(new MemberEntry(buf.readUUID(), buf.readUtf(), buf.readUtf(),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));
        }

        int roleCount = buf.readVarInt();
        List<FactionSelectionSnapshot.RoleEntry> roles = new ArrayList<>(roleCount);
        for (int i = 0; i < roleCount; i++) {
            roles.add(new FactionSelectionSnapshot.RoleEntry(
                    buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readInt(), buf.readVarInt()));
        }

        boolean viewerCanAssignRoles = buf.readBoolean();
        boolean viewerCanManageDeputies = buf.readBoolean();
        boolean viewerCanSetOrder = buf.readBoolean();
        int maxDeputies = buf.readVarInt();
        int deputyCount = buf.readVarInt();
        String orderText = buf.readUtf();
        String orderAuthor = buf.readUtf();
        int orderSecondsRemaining = buf.readInt();

        return new FactionManagementSnapshot(teamId, teamName, teamColor, canManage,
                List.copyOf(members), List.copyOf(roles),
                viewerCanAssignRoles, viewerCanManageDeputies, viewerCanSetOrder,
                maxDeputies, deputyCount, orderText, orderAuthor, orderSecondsRemaining);
    }
}
