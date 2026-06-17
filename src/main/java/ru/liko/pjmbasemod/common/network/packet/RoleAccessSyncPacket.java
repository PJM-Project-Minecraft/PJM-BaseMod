package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.List;

/** S→C: какие донат-роли игрок может назначить себе сам (владеет ими). */
public record RoleAccessSyncPacket(List<String> selfAssignableRoles) implements CustomPacketPayload {

    public static final Type<RoleAccessSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "role_access_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoleAccessSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RoleAccessSyncPacket::selfAssignableRoles,
                    RoleAccessSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
