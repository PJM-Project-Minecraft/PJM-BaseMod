package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * S→C: данные actionbar-индикации Радио-детектора. Шлется каждому игроку с детектором
 * в руке во время активного события «радиоразведка».
 *
 * @param active           есть ли активный маяк в зоне действия детектора
 * @param signalStrength   сила сигнала 0..1 (1.0 = внутри signalRadius маяка)
 * @param direction        yRot-направление (градусы) к ближайшему маяку
 * @param captureReady     игрок в радиусе захвата (можно ПКМ)
 * @param captureProgress  накопленные секунды канала перехвата этим игроком
 * @param captureSeconds   всего секунд для перехвата
 * @param capturedCount    перехвачено маяков
 * @param beaconCount      всего маяков
 */
public record SignalHuntHudPacket(
        boolean active,
        double signalStrength,
        float direction,
        boolean captureReady,
        int captureProgress,
        int captureSeconds,
        int capturedCount,
        int beaconCount
) implements CustomPacketPayload {

    public static final Type<SignalHuntHudPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "signal_hunt_hud"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SignalHuntHudPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.active);
                        buf.writeDouble(p.signalStrength);
                        buf.writeFloat(p.direction);
                        buf.writeBoolean(p.captureReady);
                        buf.writeVarInt(p.captureProgress);
                        buf.writeVarInt(p.captureSeconds);
                        buf.writeVarInt(p.capturedCount);
                        buf.writeVarInt(p.beaconCount);
                    },
                    buf -> new SignalHuntHudPacket(
                            buf.readBoolean(),
                            buf.readDouble(),
                            buf.readFloat(),
                            buf.readBoolean(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt()));

    /** События нет / детектор вне зоны — скрыть actionbar. */
    public static SignalHuntHudPacket inactive() {
        return new SignalHuntHudPacket(false, 0, 0, false, 0, 0, 0, 0);
    }

    /** Нет маяка в зоне действия — режим поиска без направления. */
    public static SignalHuntHudPacket searching(int beaconCount, int capturedCount) {
        return new SignalHuntHudPacket(false, 0, 0, false, 0, 0, capturedCount, beaconCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
