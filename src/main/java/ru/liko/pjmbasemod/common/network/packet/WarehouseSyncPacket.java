package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.warehouse.WarehouseSnapshot;

/** Сервер → клиент: обновить открытый GUI склада. */
public record WarehouseSyncPacket(WarehouseSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<WarehouseSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "warehouse_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WarehouseSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> WarehouseSnapshot.write(buf, packet.snapshot()),
            buf -> new WarehouseSyncPacket(WarehouseSnapshot.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
