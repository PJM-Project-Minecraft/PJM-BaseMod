package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.report.ReportSnapshot;

/** Сервер → клиент: обновить открытый админский GUI жалоб новым снимком. */
public record ReportSyncPacket(ReportSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<ReportSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "report_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReportSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> ReportSnapshot.write(buf, packet.snapshot()),
            buf -> new ReportSyncPacket(ReportSnapshot.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
