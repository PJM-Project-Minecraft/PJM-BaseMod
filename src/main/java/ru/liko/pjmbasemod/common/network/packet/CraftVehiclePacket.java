package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** Клиент → сервер: собрать (скрафтить) технику по id определения. */
public record CraftVehiclePacket(String defId) implements CustomPacketPayload {

    public static final Type<CraftVehiclePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "craft_vehicle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftVehiclePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, CraftVehiclePacket::defId,
                    CraftVehiclePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
