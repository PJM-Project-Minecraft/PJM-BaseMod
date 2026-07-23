package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * S→C: предупреждение о летящей ракете для зоны поражения на карте.
 * Своей команде уходит мгновенно при пуске (с названием ракеты), остальным —
 * с задержкой ~8.5 с и без названия. Клиент держит зону ~25 секунд.
 */
public record MissileAlertPacket(String dimension, double x, double z, float radius,
                                 String missileName, boolean ownTeam)
        implements CustomPacketPayload {

    public static final Type<MissileAlertPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "missile_alert"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissileAlertPacket> STREAM_CODEC =
            StreamCodec.of(MissileAlertPacket::write, MissileAlertPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, MissileAlertPacket packet) {
        buf.writeUtf(packet.dimension, 256);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.z);
        buf.writeFloat(packet.radius);
        buf.writeUtf(packet.missileName, 128);
        buf.writeBoolean(packet.ownTeam);
    }

    private static MissileAlertPacket read(RegistryFriendlyByteBuf buf) {
        return new MissileAlertPacket(buf.readUtf(256), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readUtf(128), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
