package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

/** C→S: командир/зам с правом KICK исключает участника из фракции через GUI управления. */
public record ManageFactionKickPacket(UUID targetId) implements CustomPacketPayload {

    public static final Type<ManageFactionKickPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "manage_faction_kick"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ManageFactionKickPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, packet -> packet.targetId().toString(),
                    targetId -> new ManageFactionKickPacket(UUID.fromString(targetId)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
