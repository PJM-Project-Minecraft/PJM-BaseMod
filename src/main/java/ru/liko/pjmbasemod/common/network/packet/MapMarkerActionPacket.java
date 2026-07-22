package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.Util;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

/**
 * C→S: действие с тактической меткой на карте.
 * PLACE — поставить метку типа {@code markerType} в ({@code x},{@code z});
 * для стрелки ({@code x2},{@code z2}) — второй конец, у точечных меток совпадает с первым;
 * REMOVE — убрать метку {@code id} своей команды (свою; командир/OP — любую);
 * REQUEST — запросить полный синк (при открытии карты).
 */
public record MapMarkerActionPacket(Action action, String markerType, int x, int z, int x2, int z2, UUID id)
        implements CustomPacketPayload {

    public enum Action { PLACE, REMOVE, REQUEST }

    public static MapMarkerActionPacket place(String type, int x, int z) {
        return new MapMarkerActionPacket(Action.PLACE, type, x, z, x, z, Util.NIL_UUID);
    }

    public static MapMarkerActionPacket placeArrow(int x, int z, int x2, int z2) {
        return new MapMarkerActionPacket(Action.PLACE, "arrow", x, z, x2, z2, Util.NIL_UUID);
    }

    public static MapMarkerActionPacket remove(UUID id) {
        return new MapMarkerActionPacket(Action.REMOVE, "", 0, 0, 0, 0, id);
    }

    public static MapMarkerActionPacket request() {
        return new MapMarkerActionPacket(Action.REQUEST, "", 0, 0, 0, 0, Util.NIL_UUID);
    }

    public static final Type<MapMarkerActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "map_marker_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapMarkerActionPacket> STREAM_CODEC =
            StreamCodec.of(MapMarkerActionPacket::write, MapMarkerActionPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, MapMarkerActionPacket p) {
        buf.writeEnum(p.action);
        buf.writeUtf(p.markerType);
        buf.writeVarInt(p.x);
        buf.writeVarInt(p.z);
        buf.writeVarInt(p.x2);
        buf.writeVarInt(p.z2);
        buf.writeUUID(p.id);
    }

    private static MapMarkerActionPacket read(RegistryFriendlyByteBuf buf) {
        return new MapMarkerActionPacket(buf.readEnum(Action.class), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUUID());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
