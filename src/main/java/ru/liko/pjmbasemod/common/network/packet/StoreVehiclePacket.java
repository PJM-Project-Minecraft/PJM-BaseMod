package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** Клиент → сервер: убрать ближайшую технику из зоны хранения текущего гаража. */
public record StoreVehiclePacket() implements CustomPacketPayload {

    public static final StoreVehiclePacket INSTANCE = new StoreVehiclePacket();
    public static final Type<StoreVehiclePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "store_vehicle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StoreVehiclePacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
