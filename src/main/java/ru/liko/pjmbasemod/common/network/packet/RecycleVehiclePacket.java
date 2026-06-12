package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

/** Клиент → сервер: распилить сохранённую технику на металл. */
public record RecycleVehiclePacket(UUID instanceId) implements CustomPacketPayload {

    public static final Type<RecycleVehiclePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "recycle_vehicle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecycleVehiclePacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, RecycleVehiclePacket::instanceId,
                    RecycleVehiclePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
