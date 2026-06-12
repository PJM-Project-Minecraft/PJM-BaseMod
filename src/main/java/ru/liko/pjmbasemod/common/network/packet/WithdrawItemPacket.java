package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** Клиент → сервер: получить со склада {@code count} предметов по id определения. */
public record WithdrawItemPacket(String defId, int count) implements CustomPacketPayload {

    public static final Type<WithdrawItemPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "withdraw_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawItemPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, WithdrawItemPacket::defId,
                    ByteBufCodecs.VAR_INT, WithdrawItemPacket::count,
                    WithdrawItemPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
