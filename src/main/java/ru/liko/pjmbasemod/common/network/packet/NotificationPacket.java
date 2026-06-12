package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

public record NotificationPacket(Component title, Component subtitle, int color, long durationMs)
        implements CustomPacketPayload {

    public static final Type<NotificationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "notification"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NotificationPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.STREAM_CODEC, NotificationPacket::title,
                    ComponentSerialization.STREAM_CODEC, NotificationPacket::subtitle,
                    ByteBufCodecs.INT, NotificationPacket::color,
                    ByteBufCodecs.VAR_LONG, NotificationPacket::durationMs,
                    NotificationPacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
