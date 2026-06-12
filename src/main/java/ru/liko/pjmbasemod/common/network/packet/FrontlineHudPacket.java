package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

public record FrontlineHudPacket(
        boolean visible,
        boolean inRegion,
        boolean captureActive,
        String regionName,
        int sectorX,
        int sectorZ,
        String ownerName,
        int ownerColor,
        String captureName,
        int captureColor,
        String status,
        int progressPercent,
        int secondsRemaining,
        String countsText
) implements CustomPacketPayload {

    public static final Type<FrontlineHudPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "frontline_hud"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FrontlineHudPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.visible());
                buf.writeBoolean(packet.inRegion());
                buf.writeBoolean(packet.captureActive());
                buf.writeUtf(packet.regionName());
                buf.writeVarInt(packet.sectorX());
                buf.writeVarInt(packet.sectorZ());
                buf.writeUtf(packet.ownerName());
                buf.writeVarInt(packet.ownerColor());
                buf.writeUtf(packet.captureName());
                buf.writeVarInt(packet.captureColor());
                buf.writeUtf(packet.status());
                buf.writeVarInt(packet.progressPercent());
                buf.writeVarInt(packet.secondsRemaining());
                buf.writeUtf(packet.countsText());
            },
            buf -> new FrontlineHudPacket(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
