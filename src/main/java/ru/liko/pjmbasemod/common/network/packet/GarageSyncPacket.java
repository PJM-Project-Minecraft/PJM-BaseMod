package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.garage.GarageSnapshot;

/** Сервер → клиент: обновить уже открытый GUI гаража после крафта/спавна. */
public record GarageSyncPacket(GarageSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<GarageSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "garage_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GarageSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> GarageSnapshot.write(buf, packet.snapshot()),
            buf -> new GarageSyncPacket(GarageSnapshot.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
