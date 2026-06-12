package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.ArrayList;
import java.util.List;

public record FrontlineMapSyncPacket(List<ChunkEntry> chunks, List<SectorEntry> sectors) implements CustomPacketPayload {

    public static final Type<FrontlineMapSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "frontline_map_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FrontlineMapSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.chunks().size());
                for (ChunkEntry chunk : packet.chunks()) {
                    buf.writeUtf(chunk.dimension());
                    buf.writeVarInt(chunk.x());
                    buf.writeVarInt(chunk.z());
                    buf.writeUtf(chunk.ownerId());
                    buf.writeUtf(chunk.ownerName());
                    buf.writeVarInt(chunk.ownerColor());
                }

                buf.writeVarInt(packet.sectors().size());
                for (SectorEntry sector : packet.sectors()) {
                    buf.writeUtf(sector.dimension());
                    buf.writeUtf(sector.regionName());
                    buf.writeVarInt(sector.sectorX());
                    buf.writeVarInt(sector.sectorZ());
                    buf.writeVarInt(sector.minX());
                    buf.writeVarInt(sector.minZ());
                    buf.writeVarInt(sector.maxX());
                    buf.writeVarInt(sector.maxZ());
                    buf.writeUtf(sector.teamName());
                    buf.writeVarInt(sector.teamColor());
                    buf.writeVarInt(sector.progressPercent());
                    buf.writeBoolean(sector.contested());
                }
            },
            buf -> {
                int chunkCount = buf.readVarInt();
                List<ChunkEntry> chunks = new ArrayList<>(chunkCount);
                for (int i = 0; i < chunkCount; i++) {
                    chunks.add(new ChunkEntry(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readVarInt()));
                }

                int sectorCount = buf.readVarInt();
                List<SectorEntry> sectors = new ArrayList<>(sectorCount);
                for (int i = 0; i < sectorCount; i++) {
                    sectors.add(new SectorEntry(
                            buf.readUtf(), buf.readUtf(),
                            buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
                }
                return new FrontlineMapSyncPacket(List.copyOf(chunks), List.copyOf(sectors));
            }
    );

    public record ChunkEntry(String dimension, int x, int z, String ownerId, String ownerName, int ownerColor) {}
    public record SectorEntry(
            String dimension,
            String regionName,
            int sectorX,
            int sectorZ,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            String teamName,
            int teamColor,
            int progressPercent,
            boolean contested
    ) {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
