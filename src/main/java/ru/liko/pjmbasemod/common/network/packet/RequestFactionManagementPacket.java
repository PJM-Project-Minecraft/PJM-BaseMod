package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** Клиент → сервер: открыть экран управления фракцией (для командира/зама; права проверяет сервер). */
public record RequestFactionManagementPacket() implements CustomPacketPayload {

    public static final RequestFactionManagementPacket INSTANCE = new RequestFactionManagementPacket();
    public static final Type<RequestFactionManagementPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "request_faction_management"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFactionManagementPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
