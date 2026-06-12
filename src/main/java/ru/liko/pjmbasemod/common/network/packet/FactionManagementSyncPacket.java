package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.faction.FactionManagementSnapshot;

public record FactionManagementSyncPacket(FactionManagementSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<FactionManagementSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "faction_management_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FactionManagementSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> FactionManagementSnapshot.write(buf, packet.snapshot()),
            buf -> new FactionManagementSyncPacket(FactionManagementSnapshot.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
