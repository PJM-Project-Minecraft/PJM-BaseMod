package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

public record RoleSyncPacket(UUID playerId, String currentRole, boolean canAssignRoles)
        implements CustomPacketPayload {

    public static final Type<RoleSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "role_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoleSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, packet -> packet.playerId().toString(),
                    ByteBufCodecs.STRING_UTF8, RoleSyncPacket::currentRole,
                    ByteBufCodecs.BOOL, RoleSyncPacket::canAssignRoles,
                    (playerId, currentRole, canAssignRoles) -> new RoleSyncPacket(
                            UUID.fromString(playerId), currentRole, canAssignRoles));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
