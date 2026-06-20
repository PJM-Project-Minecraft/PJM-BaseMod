package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.List;
import java.util.UUID;

/** S→C: id ролей, которые командир может назначить указанной цели (бесплатные + платные, которыми цель владеет). */
public record TargetRoleAccessPacket(UUID targetId, List<String> assignableRoles) implements CustomPacketPayload {

    public static final Type<TargetRoleAccessPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "target_role_access"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TargetRoleAccessPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, packet -> packet.targetId().toString(),
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TargetRoleAccessPacket::assignableRoles,
                    (targetId, assignableRoles) -> new TargetRoleAccessPacket(UUID.fromString(targetId), assignableRoles));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
