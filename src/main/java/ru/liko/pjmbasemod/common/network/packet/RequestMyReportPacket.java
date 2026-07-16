package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** Клиент → сервер: открыть переписку игрока с администрацией (своё активное обращение). */
public record RequestMyReportPacket() implements CustomPacketPayload {

    public static final RequestMyReportPacket INSTANCE = new RequestMyReportPacket();
    public static final Type<RequestMyReportPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "request_my_report"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMyReportPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
