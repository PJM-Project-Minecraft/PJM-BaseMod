package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

public record ManageFactionRolePacket(UUID targetId, String roleId) implements CustomPacketPayload {

    public static final Type<ManageFactionRolePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "manage_faction_role"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ManageFactionRolePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, packet -> packet.targetId().toString(),
                    ByteBufCodecs.STRING_UTF8, ManageFactionRolePacket::roleId,
                    (targetId, roleId) -> new ManageFactionRolePacket(UUID.fromString(targetId), roleId));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
