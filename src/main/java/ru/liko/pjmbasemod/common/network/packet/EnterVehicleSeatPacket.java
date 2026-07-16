package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** C→S: игрок выбрал место в технике SuperbWarfare через радиальное меню. */
public record EnterVehicleSeatPacket(int vehicleId, int seat) implements CustomPacketPayload {

    public static final Type<EnterVehicleSeatPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "enter_vehicle_seat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnterVehicleSeatPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EnterVehicleSeatPacket::vehicleId,
                    ByteBufCodecs.VAR_INT, EnterVehicleSeatPacket::seat,
                    EnterVehicleSeatPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
