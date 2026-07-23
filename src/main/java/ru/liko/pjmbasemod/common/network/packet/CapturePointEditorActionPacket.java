package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.capturepoint.CapturePoint;

import java.util.ArrayList;
import java.util.List;

/** C→S: действие редактора точек захвата (OP-only, level 2). */
public record CapturePointEditorActionPacket(
        Action action,
        String pointId,
        String displayName,
        String dimension,
        String ownerTeamId,
        List<CapturePoint.Vertex> vertices,
        int order,
        String linkTargetId
) implements CustomPacketPayload {

    public enum Action { ADD, REMOVE, UPDATE_VERTICES, UPDATE_DISPLAY_NAME, SET_OWNER, SET_ORDER, TOGGLE_LINK }

    public static final Type<CapturePointEditorActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "capturepoint_editor_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CapturePointEditorActionPacket> STREAM_CODEC =
            StreamCodec.of(CapturePointEditorActionPacket::write, CapturePointEditorActionPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, CapturePointEditorActionPacket p) {
        buf.writeEnum(p.action);
        buf.writeUtf(p.pointId);
        buf.writeUtf(p.displayName);
        buf.writeUtf(p.dimension);
        buf.writeUtf(p.ownerTeamId);
        buf.writeVarInt(p.vertices.size());
        for (CapturePoint.Vertex v : p.vertices) {
            buf.writeVarInt(v.x());
            buf.writeVarInt(v.z());
        }
        buf.writeVarInt(p.order);
        buf.writeUtf(p.linkTargetId);
    }

    private static CapturePointEditorActionPacket read(RegistryFriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        String pointId = buf.readUtf();
        String displayName = buf.readUtf();
        String dimension = buf.readUtf();
        String ownerTeamId = buf.readUtf();
        int vcount = buf.readVarInt();
        List<CapturePoint.Vertex> vertices = new ArrayList<>(vcount);
        for (int i = 0; i < vcount; i++) {
            vertices.add(new CapturePoint.Vertex(buf.readVarInt(), buf.readVarInt()));
        }
        int order = buf.readVarInt();
        String linkTargetId = buf.readUtf();
        return new CapturePointEditorActionPacket(action, pointId, displayName, dimension,
                ownerTeamId, List.copyOf(vertices), order, linkTargetId);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
