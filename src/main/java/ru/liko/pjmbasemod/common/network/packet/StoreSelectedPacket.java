package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

/** Клиент → сервер: убрать в гараж конкретную выбранную в меню технику (по id сущности в мире). */
public record StoreSelectedPacket(UUID entityId) implements CustomPacketPayload {

    public static final Type<StoreSelectedPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "store_selected"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StoreSelectedPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, StoreSelectedPacket::entityId,
                    StoreSelectedPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
