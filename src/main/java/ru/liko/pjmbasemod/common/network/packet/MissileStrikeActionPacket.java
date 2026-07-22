package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** C→S: запрос каталога либо подтверждённый пуск по координатам текущего измерения. */
public record MissileStrikeActionPacket(Action action, String missileId, int x, int z)
        implements CustomPacketPayload {

    public enum Action { REQUEST, LAUNCH }

    public static MissileStrikeActionPacket request() {
        return new MissileStrikeActionPacket(Action.REQUEST, "", 0, 0);
    }

    public static MissileStrikeActionPacket launch(String missileId, int x, int z) {
        return new MissileStrikeActionPacket(Action.LAUNCH, missileId, x, z);
    }

    public static final Type<MissileStrikeActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "missile_strike_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissileStrikeActionPacket> STREAM_CODEC =
            StreamCodec.of(MissileStrikeActionPacket::write, MissileStrikeActionPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, MissileStrikeActionPacket packet) {
        buf.writeEnum(packet.action);
        buf.writeUtf(packet.missileId, 64);
        buf.writeInt(packet.x);
        buf.writeInt(packet.z);
    }

    private static MissileStrikeActionPacket read(RegistryFriendlyByteBuf buf) {
        return new MissileStrikeActionPacket(buf.readEnum(Action.class), buf.readUtf(64),
                buf.readInt(), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
