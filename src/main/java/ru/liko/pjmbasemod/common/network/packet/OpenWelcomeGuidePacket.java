package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** Сервер → клиент: показать анимированное руководство по серверу при входе. Без полезной нагрузки. */
public record OpenWelcomeGuidePacket() implements CustomPacketPayload {

    public static final OpenWelcomeGuidePacket INSTANCE = new OpenWelcomeGuidePacket();

    public static final Type<OpenWelcomeGuidePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "open_welcome_guide"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWelcomeGuidePacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
