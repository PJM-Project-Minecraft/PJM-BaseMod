package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

/** Клиент → сервер: заспавнить технику из гаража по id экземпляра. */
public record SpawnVehiclePacket(UUID instanceId) implements CustomPacketPayload {

    public static final Type<SpawnVehiclePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "spawn_vehicle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnVehiclePacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, SpawnVehiclePacket::instanceId,
                    SpawnVehiclePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
