package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * Клиент → сервер: действие админа над жалобой из GUI.
 * {@code action}: {@code "teleport"} / {@code "close"} / {@code "reply"}.
 * {@code text} используется только для ответа. Права проверяет сервер.
 */
public record ReportActionPacket(int id, String action, String text) implements CustomPacketPayload {

    public static final Type<ReportActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "report_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReportActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.id());
                buf.writeUtf(p.action());
                buf.writeUtf(p.text());
            },
            buf -> new ReportActionPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
