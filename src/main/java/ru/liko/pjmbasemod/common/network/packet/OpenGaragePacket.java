package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.garage.GarageSnapshot;

/** Сервер → клиент: открыть GUI гаража с переданным снимком состояния. */
public record OpenGaragePacket(GarageSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<OpenGaragePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "open_garage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGaragePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> GarageSnapshot.write(buf, packet.snapshot()),
            buf -> new OpenGaragePacket(GarageSnapshot.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
