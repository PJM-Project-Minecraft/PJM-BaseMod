package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.capturepoint.CapturePoint;

import java.util.ArrayList;
import java.util.List;

/** S→C: открыть экран редактора точек захвата (OP-only). Несёт текущие точки. */
public record OpenCapturePointEditorPacket(List<CapturePoint> points) implements CustomPacketPayload {

    public static final Type<OpenCapturePointEditorPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "open_capturepoint_editor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCapturePointEditorPacket> STREAM_CODEC =
            StreamCodec.of(OpenCapturePointEditorPacket::write, OpenCapturePointEditorPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, OpenCapturePointEditorPacket p) {
        buf.writeVarInt(p.points.size());
        for (CapturePoint cp : p.points) {
            buf.writeUtf(cp.id());
            buf.writeUtf(cp.displayName());
            buf.writeUtf(cp.dimension());
            buf.writeVarInt(cp.vertices().size());
            for (CapturePoint.Vertex v : cp.vertices()) {
                buf.writeVarInt(v.x());
                buf.writeVarInt(v.z());
            }
            buf.writeUtf(cp.ownerTeamId());
            buf.writeVarInt(cp.ownerColor());
            buf.writeUtf(cp.captureTeamId());
            buf.writeVarInt(cp.progressPercent());
            buf.writeBoolean(cp.contested());
        }
    }

    private static OpenCapturePointEditorPacket read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<CapturePoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf();
            String displayName = buf.readUtf();
            String dimension = buf.readUtf();
            int vcount = buf.readVarInt();
            List<CapturePoint.Vertex> vertices = new ArrayList<>(vcount);
            for (int j = 0; j < vcount; j++) {
                vertices.add(new CapturePoint.Vertex(buf.readVarInt(), buf.readVarInt()));
            }
            points.add(new CapturePoint(id, displayName, dimension, List.copyOf(vertices),
                    buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readBoolean()));
        }
        return new OpenCapturePointEditorPacket(List.copyOf(points));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
