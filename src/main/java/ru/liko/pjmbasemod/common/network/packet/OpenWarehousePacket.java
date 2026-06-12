package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.warehouse.WarehouseSnapshot;

/** Сервер → клиент: открыть GUI склада с переданным снимком состояния. */
public record OpenWarehousePacket(WarehouseSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<OpenWarehousePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "open_warehouse"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWarehousePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> WarehouseSnapshot.write(buf, packet.snapshot()),
            buf -> new OpenWarehousePacket(WarehouseSnapshot.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
