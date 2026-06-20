package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

public record FactionOrderSyncPacket(
        boolean active,
        String text,
        String author,
        int teamColor,
        int secondsRemaining
) implements CustomPacketPayload {

    public static final Type<FactionOrderSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "faction_order_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionOrderSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.active());
                buf.writeUtf(packet.text());
                buf.writeUtf(packet.author());
                buf.writeVarInt(packet.teamColor());
                buf.writeInt(packet.secondsRemaining());
            },
            buf -> new FactionOrderSyncPacket(
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
