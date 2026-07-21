package ru.liko.pjmbasemod.client.worldmap.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import ru.liko.pjmbasemod.client.worldmap.data.MapConstants;
import ru.liko.pjmbasemod.client.worldmap.data.Region;
import ru.liko.pjmbasemod.client.worldmap.data.RegionKey;

/**
 * Сериализация региона в gzip-байты и обратно. Формат (после распаковки):
 * <pre>
 *   int  FORMAT_VERSION
 *   1024 чанков (cz*32+cx, row-major):
 *     boolean present
 *     if present: 256 пикселей (z*16+x, row-major): { int baseColor; short height }  // 6 б/пиксель
 * </pre>
 * Неисследованный чанк — present=false (без данных). Регион без чанков не пишется (см. RegionStore).
 */
public final class RegionCodec {

    private RegionCodec() {}

    public static byte[] encode(Region region) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bos))) {
            out.writeInt(MapConstants.FORMAT_VERSION);
            for (int cz = 0; cz < MapConstants.CHUNKS_PER_REGION; cz++) {
                for (int cx = 0; cx < MapConstants.CHUNKS_PER_REGION; cx++) {
                    boolean present = region.isChunkScanned(cx, cz);
                    out.writeBoolean(present);
                    if (present) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                int idx = (cz * 16 + z) * MapConstants.REGION_BLOCKS + (cx * 16 + x);
                                out.writeInt(region.baseColor[idx]);
                                out.writeShort(region.height[idx]);
                            }
                        }
                    }
                }
            }
        }
        return bos.toByteArray();
    }

    /** @return регион, либо null при несовместимом формате (→ перескан). */
    public static Region decode(RegionKey key, byte[] data) throws IOException {
        Region region = new Region(key);
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            int version = in.readInt();
            if (version != MapConstants.FORMAT_VERSION) return null;
            for (int cz = 0; cz < MapConstants.CHUNKS_PER_REGION; cz++) {
                for (int cx = 0; cx < MapConstants.CHUNKS_PER_REGION; cx++) {
                    boolean present = in.readBoolean();
                    if (present) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                int idx = (cz * 16 + z) * MapConstants.REGION_BLOCKS + (cx * 16 + x);
                                region.baseColor[idx] = in.readInt();
                                region.height[idx] = in.readShort();
                            }
                        }
                        region.markChunkScanned(cx, cz);
                    }
                }
            }
        }
        return region;
    }
}
