package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

public record RefillAmmunitionPacket() implements CustomPacketPayload {

    public static final RefillAmmunitionPacket INSTANCE = new RefillAmmunitionPacket();
    public static final Type<RefillAmmunitionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "refill_ammunition"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RefillAmmunitionPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
