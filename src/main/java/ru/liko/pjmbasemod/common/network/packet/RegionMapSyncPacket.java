package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.ArrayList;
import java.util.List;

public record RegionMapSyncPacket(List<RegionEntry> regions) implements CustomPacketPayload {

    public static final Type<RegionMapSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "region_map_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RegionMapSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.regions().size());
                for (RegionEntry region : packet.regions()) {
                    buf.writeUtf(region.name());
                    buf.writeUtf(region.displayName());
                    buf.writeUtf(region.dimension());
                    buf.writeVarInt(region.minX());
                    buf.writeVarInt(region.minZ());
                    buf.writeVarInt(region.maxX());
                    buf.writeVarInt(region.maxZ());
                    buf.writeBoolean(region.frontline());
                }
            },
            buf -> {
                int regionCount = buf.readVarInt();
                List<RegionEntry> regions = new ArrayList<>(regionCount);
                for (int i = 0; i < regionCount; i++) {
                    regions.add(new RegionEntry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
                }
                return new RegionMapSyncPacket(List.copyOf(regions));
            }
    );

    public record RegionEntry(String name, String displayName, String dimension, int minX, int minZ, int maxX, int maxZ, boolean frontline) {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
