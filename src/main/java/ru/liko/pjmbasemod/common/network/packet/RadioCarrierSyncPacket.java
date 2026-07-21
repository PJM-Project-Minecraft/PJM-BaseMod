package ru.liko.pjmbasemod.common.network.packet;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * S→C: активные носители рации команды-получателя, находящиеся в его измерении — для отображения
 * точек десанта на карте. Переиспользует {@link RadioSpawnListPacket.Entry} (id/owner/pos/cooldown).
 */
public record RadioCarrierSyncPacket(List<RadioSpawnListPacket.Entry> carriers) implements CustomPacketPayload {

    public static final Type<RadioCarrierSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "radio_carrier_sync"));

    public static final StreamCodec<ByteBuf, RadioCarrierSyncPacket> STREAM_CODEC = StreamCodec.composite(
            RadioSpawnListPacket.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), RadioCarrierSyncPacket::carriers,
            RadioCarrierSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
