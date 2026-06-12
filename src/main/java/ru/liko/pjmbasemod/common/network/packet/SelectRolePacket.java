package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

public record SelectRolePacket(UUID targetId, String roleId) implements CustomPacketPayload {

    public static final Type<SelectRolePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "select_role"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectRolePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, packet -> packet.targetId().toString(),
                    ByteBufCodecs.STRING_UTF8, SelectRolePacket::roleId,
                    (targetId, roleId) -> new SelectRolePacket(UUID.fromString(targetId), roleId));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
