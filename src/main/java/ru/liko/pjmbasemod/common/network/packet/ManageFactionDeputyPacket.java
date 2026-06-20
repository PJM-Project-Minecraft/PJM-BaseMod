package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

public record ManageFactionDeputyPacket(UUID targetId, boolean deputy, int perms) implements CustomPacketPayload {

    public static final Type<ManageFactionDeputyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "manage_faction_deputy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ManageFactionDeputyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, packet -> packet.targetId().toString(),
                    ByteBufCodecs.BOOL, ManageFactionDeputyPacket::deputy,
                    ByteBufCodecs.VAR_INT, ManageFactionDeputyPacket::perms,
                    (targetId, deputy, perms) -> new ManageFactionDeputyPacket(UUID.fromString(targetId), deputy, perms));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
