package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.report.ReportSnapshot;

/** Сервер → клиент: открыть админский GUI жалоб с переданным снимком. */
public record OpenReportsPacket(ReportSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<OpenReportsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "open_reports"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenReportsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> ReportSnapshot.write(buf, packet.snapshot()),
            buf -> new OpenReportsPacket(ReportSnapshot.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
