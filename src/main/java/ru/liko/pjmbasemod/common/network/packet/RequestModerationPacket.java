package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** Клиент → сервер: открыть экран модерации (права проверяет сервер). */
public record RequestModerationPacket() implements CustomPacketPayload {

    public static final RequestModerationPacket INSTANCE = new RequestModerationPacket();
    public static final Type<RequestModerationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "request_moderation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestModerationPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
