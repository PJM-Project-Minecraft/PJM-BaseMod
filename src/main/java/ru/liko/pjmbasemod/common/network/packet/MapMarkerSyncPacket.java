package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S→C: тактические метки своей команды на карте. Полный список — пришедший
 * пакет целиком заменяет клиентское состояние.
 */
public record MapMarkerSyncPacket(List<Entry> markers) implements CustomPacketPayload {

    /**
     * @param type ключ типа метки (см. {@code MapMarkerManager.TYPES}) — иконка на клиенте.
     * @param x2/z2 второй конец стрелки (Squad-style); для точечных меток равны x/z.
     * @param commander метка поставлена командиром фракции — клиент подсвечивает.
     */
    public record Entry(UUID id, String type, int x, int z, int x2, int z2,
                        String dimension, String owner, boolean commander) {

        /** Стрелка с направлением (два разных конца). */
        public boolean directional() {
            return x2 != x || z2 != z;
        }
    }

    public static final Type<MapMarkerSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "map_marker_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapMarkerSyncPacket> STREAM_CODEC =
            StreamCodec.of(MapMarkerSyncPacket::write, MapMarkerSyncPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, MapMarkerSyncPacket p) {
        buf.writeVarInt(p.markers.size());
        for (Entry e : p.markers) {
            buf.writeUUID(e.id);
            buf.writeUtf(e.type);
            buf.writeVarInt(e.x);
            buf.writeVarInt(e.z);
            buf.writeVarInt(e.x2);
            buf.writeVarInt(e.z2);
            buf.writeUtf(e.dimension);
            buf.writeUtf(e.owner);
            buf.writeBoolean(e.commander);
        }
    }

    private static MapMarkerSyncPacket read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> markers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            markers.add(new Entry(buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readBoolean()));
        }
        return new MapMarkerSyncPacket(List.copyOf(markers));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
