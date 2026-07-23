package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** S→C: место детонации ракеты для отметки поражения на карте. */
public record MissileImpactPacket(String dimension, double x, double z, float radius, boolean shotDown)
        implements CustomPacketPayload {

    public static final Type<MissileImpactPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "missile_impact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissileImpactPacket> STREAM_CODEC =
            StreamCodec.of(MissileImpactPacket::write, MissileImpactPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, MissileImpactPacket packet) {
        buf.writeUtf(packet.dimension, 256);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.z);
        buf.writeFloat(packet.radius);
        buf.writeBoolean(packet.shotDown);
    }

    private static MissileImpactPacket read(RegistryFriendlyByteBuf buf) {
        return new MissileImpactPacket(buf.readUtf(256), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
