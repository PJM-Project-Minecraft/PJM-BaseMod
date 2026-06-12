package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.faction.FactionCommanderService;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;

import javax.annotation.Nullable;
import java.util.UUID;

public record FactionCommanderSyncPacket(
        UUID playerId,
        boolean active,
        String teamId,
        String teamName,
        int teamColor,
        String roleShortName,
        String roleDisplayName
) implements CustomPacketPayload {

    public static final Type<FactionCommanderSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "faction_commander_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionCommanderSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUUID(packet.playerId());
                buf.writeBoolean(packet.active());
                buf.writeUtf(packet.teamId());
                buf.writeUtf(packet.teamName());
                buf.writeVarInt(packet.teamColor());
                buf.writeUtf(packet.roleShortName());
                buf.writeUtf(packet.roleDisplayName());
            },
            buf -> new FactionCommanderSyncPacket(
                    buf.readUUID(),
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readUtf()
            )
    );

    public static FactionCommanderSyncPacket from(ServerPlayer player, @Nullable String teamId) {
        boolean active = teamId != null && !teamId.isBlank();
        String team = active ? teamId : "";
        return new FactionCommanderSyncPacket(
                player.getUUID(),
                active,
                team,
                active ? FrontlineTeams.displayName(player.getServer(), team) : "",
                active ? FrontlineTeams.color(player.getServer(), team) : FactionCommanderService.ROLE_COLOR,
                FactionCommanderService.ROLE_SHORT_NAME,
                FactionCommanderService.ROLE_DISPLAY_NAME
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
