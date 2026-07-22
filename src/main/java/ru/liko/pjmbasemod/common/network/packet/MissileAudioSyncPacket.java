package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

/**
 * S→C: позиция летящей ракеты для звуковой системы. Шлётся игрокам в звуковом радиусе
 * независимо от entity-трекинга, поэтому ракету слышно и вне прогруза сущности.
 * {@code active=false} — полёт закончился, трек надо остановить.
 */
public record MissileAudioSyncPacket(UUID missileId, boolean ballistic, boolean active,
                                     double x, double y, double z)
        implements CustomPacketPayload {

    public static final Type<MissileAudioSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "missile_audio_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissileAudioSyncPacket> STREAM_CODEC =
            StreamCodec.of(MissileAudioSyncPacket::write, MissileAudioSyncPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, MissileAudioSyncPacket packet) {
        buf.writeUUID(packet.missileId);
        buf.writeBoolean(packet.ballistic);
        buf.writeBoolean(packet.active);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
    }

    private static MissileAudioSyncPacket read(RegistryFriendlyByteBuf buf) {
        return new MissileAudioSyncPacket(buf.readUUID(), buf.readBoolean(), buf.readBoolean(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
