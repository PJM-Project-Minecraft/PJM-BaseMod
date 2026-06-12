package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

public record RadioSwitchPacket(boolean isPressed) implements CustomPacketPayload {

    public static final Type<RadioSwitchPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "radio_switch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadioSwitchPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, RadioSwitchPacket::isPressed,
                    RadioSwitchPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
