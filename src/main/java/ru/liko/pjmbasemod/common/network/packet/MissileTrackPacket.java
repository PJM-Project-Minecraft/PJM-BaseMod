package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

/**
 * S→C: живой трек летящей ракеты для карты — только команде, запустившей ракету.
 * Сервер шлёт раз в 10 тиков без лимита дистанции; {@code active=false} — ракета
 * детонировала или сбита, стрелку с карты убрать.
 */
public record MissileTrackPacket(UUID id, String dimension, double x, double z,
                                 float yaw, boolean active)
        implements CustomPacketPayload {

    public static final Type<MissileTrackPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "missile_track"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissileTrackPacket> STREAM_CODEC =
            StreamCodec.of(MissileTrackPacket::write, MissileTrackPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, MissileTrackPacket packet) {
        buf.writeUUID(packet.id);
        buf.writeUtf(packet.dimension, 256);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.z);
        buf.writeFloat(packet.yaw);
        buf.writeBoolean(packet.active);
    }

    private static MissileTrackPacket read(RegistryFriendlyByteBuf buf) {
        return new MissileTrackPacket(buf.readUUID(), buf.readUtf(256),
                buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
