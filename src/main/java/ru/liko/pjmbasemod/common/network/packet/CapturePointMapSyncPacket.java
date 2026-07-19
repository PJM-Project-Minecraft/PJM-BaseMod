package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.capturepoint.CapturePoint;

import java.util.ArrayList;
import java.util.List;

/**
 * S→C: полная синхронизация всех точек захвата для отображения на карте.
 * {@code sequential} — серверный флаг последовательного захвата (конфиг не синкается
 * на клиент, поэтому едет в пакете): клиент по нему решает, показывать ли цепочку.
 */
public record CapturePointMapSyncPacket(List<CapturePoint> points, boolean sequential) implements CustomPacketPayload {

    public static final Type<CapturePointMapSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "capturepoint_map_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CapturePointMapSyncPacket> STREAM_CODEC =
            StreamCodec.of(CapturePointMapSyncPacket::write, CapturePointMapSyncPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, CapturePointMapSyncPacket p) {
        buf.writeBoolean(p.sequential);
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
            buf.writeVarInt(cp.order());
        }
    }

    private static CapturePointMapSyncPacket read(RegistryFriendlyByteBuf buf) {
        boolean sequential = buf.readBoolean();
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
            String owner = buf.readUtf();
            int ownerColor = buf.readVarInt();
            String capture = buf.readUtf();
            int progress = buf.readVarInt();
            boolean contested = buf.readBoolean();
            int order = buf.readVarInt();
            points.add(new CapturePoint(id, displayName, dimension, List.copyOf(vertices),
                    owner, ownerColor, capture, progress, contested, order));
        }
        return new CapturePointMapSyncPacket(List.copyOf(points), sequential);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
