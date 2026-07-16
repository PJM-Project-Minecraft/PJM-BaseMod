package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.report.ReportThread;

/** Сервер → клиент: открыть/обновить переписку игрока (его обращение). */
public record PlayerReportThreadPacket(ReportThread thread) implements CustomPacketPayload {

    public static final Type<PlayerReportThreadPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "player_report_thread"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerReportThreadPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> ReportThread.write(buf, packet.thread()),
            buf -> new PlayerReportThreadPacket(ReportThread.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
