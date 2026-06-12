package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

public record RadioEventPacket(UUID senderId, boolean isStart) implements CustomPacketPayload {

    public static final Type<RadioEventPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "radio_event"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadioEventPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, RadioEventPacket::senderId,
                    ByteBufCodecs.BOOL, RadioEventPacket::isStart,
                    RadioEventPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
