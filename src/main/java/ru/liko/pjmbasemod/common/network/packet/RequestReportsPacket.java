package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** Клиент → сервер: открыть админский GUI жалоб (права проверяет сервер). */
public record RequestReportsPacket() implements CustomPacketPayload {

    public static final RequestReportsPacket INSTANCE = new RequestReportsPacket();
    public static final Type<RequestReportsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "request_reports"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestReportsPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
