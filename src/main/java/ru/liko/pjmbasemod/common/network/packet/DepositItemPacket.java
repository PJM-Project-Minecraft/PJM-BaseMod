package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** Клиент → сервер: сдать на склад {@code count} целых пачек по id определения, получив очки. */
public record DepositItemPacket(String defId, int count) implements CustomPacketPayload {

    public static final Type<DepositItemPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "deposit_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DepositItemPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, DepositItemPacket::defId,
                    ByteBufCodecs.VAR_INT, DepositItemPacket::count,
                    DepositItemPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
