package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * S→C: зона активного серверного события для отображения на карте.
 * {@code active=false} — событие закончилось, убрать оверлей.
 */
public record EventMapSyncPacket(
        boolean active,
        String typeId,
        String pointName,
        String dimension,
        int centerX,
        int centerY,
        int centerZ,
        int radius
) implements CustomPacketPayload {

    public static final Type<EventMapSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "event_map_sync"));

    // composite() ограничен 6 полями — кодек вручную.
    public static final StreamCodec<RegistryFriendlyByteBuf, EventMapSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.active);
                        buf.writeUtf(p.typeId);
                        buf.writeUtf(p.pointName);
                        buf.writeUtf(p.dimension);
                        buf.writeVarInt(p.centerX);
                        buf.writeVarInt(p.centerY);
                        buf.writeVarInt(p.centerZ);
                        buf.writeVarInt(p.radius);
                    },
                    buf -> new EventMapSyncPacket(
                            buf.readBoolean(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt())
            );

    public static EventMapSyncPacket inactive() {
        return new EventMapSyncPacket(false, "", "", "", 0, 0, 0, 0);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
