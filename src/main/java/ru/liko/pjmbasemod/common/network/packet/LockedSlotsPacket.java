package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client: список заблокированных слотов инвентаря + флаги поведения.
 * Клиент рисует поверх этих слотов иконку барьера и (опционально) отменяет клики.
 */
public record LockedSlotsPacket(
        boolean enabled,
        boolean cancelClicks,
        List<Integer> lockedSlots
) implements CustomPacketPayload {

    public static final Type<LockedSlotsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "locked_slots"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LockedSlotsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.enabled());
                buf.writeBoolean(packet.cancelClicks());
                buf.writeVarInt(packet.lockedSlots().size());
                for (int slot : packet.lockedSlots()) {
                    buf.writeVarInt(slot);
                }
            },
            buf -> {
                boolean enabled = buf.readBoolean();
                boolean cancelClicks = buf.readBoolean();
                int size = buf.readVarInt();
                List<Integer> slots = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    slots.add(buf.readVarInt());
                }
                return new LockedSlotsPacket(enabled, cancelClicks, slots);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
