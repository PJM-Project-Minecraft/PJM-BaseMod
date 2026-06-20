package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

/** C→S: командир запрашивает, какие роли он может назначить выбранной цели (бесплатные + платные, которыми цель владеет). */
public record RequestTargetRoleAccessPacket(UUID targetId) implements CustomPacketPayload {

    public static final Type<RequestTargetRoleAccessPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "request_target_role_access"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTargetRoleAccessPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, packet -> packet.targetId().toString(),
                    targetId -> new RequestTargetRoleAccessPacket(UUID.fromString(targetId)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
