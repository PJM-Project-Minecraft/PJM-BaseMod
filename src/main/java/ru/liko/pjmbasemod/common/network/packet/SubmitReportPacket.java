package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.report.ReportCategory;

/** Клиент → сервер: игрок отправляет обращение в администрацию. */
public record SubmitReportPacket(ReportCategory category, String text) implements CustomPacketPayload {

    public static final Type<SubmitReportPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "submit_report"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitReportPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeEnum(p.category());
                buf.writeUtf(p.text());
            },
            buf -> new SubmitReportPacket(buf.readEnum(ReportCategory.class), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
