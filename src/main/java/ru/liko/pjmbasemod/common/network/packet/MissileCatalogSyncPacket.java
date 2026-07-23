package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.ArrayList;
import java.util.List;

/** S→C: серверный каталог ракет и актуальная доступность удара для игрока. */
public record MissileCatalogSyncPacket(
        boolean authorized,
        boolean sbwAvailable,
        boolean activeStrike,
        String warehouseId,
        int supplyPoints,
        List<Entry> entries
) implements CustomPacketPayload {

    /** {@code remainingCooldown} — остаток перезарядки ИМЕННО этой ракеты для команды игрока, сек. */
    public record Entry(String id, String displayName, String translationKey,
                        int supplyCost, int cooldownSeconds, long remainingCooldown,
                        int flightSeconds, float radius, boolean ballistic) {}

    public static final Type<MissileCatalogSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "missile_catalog_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissileCatalogSyncPacket> STREAM_CODEC =
            StreamCodec.of(MissileCatalogSyncPacket::write, MissileCatalogSyncPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, MissileCatalogSyncPacket packet) {
        buf.writeBoolean(packet.authorized);
        buf.writeBoolean(packet.sbwAvailable);
        buf.writeBoolean(packet.activeStrike);
        buf.writeUtf(packet.warehouseId, 128);
        buf.writeVarInt(Math.max(0, packet.supplyPoints));
        buf.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buf.writeUtf(entry.id, 64);
            buf.writeUtf(entry.displayName, 128);
            buf.writeUtf(entry.translationKey, 160);
            buf.writeVarInt(entry.supplyCost);
            buf.writeVarInt(entry.cooldownSeconds);
            buf.writeVarLong(entry.remainingCooldown);
            buf.writeVarInt(entry.flightSeconds);
            buf.writeFloat(entry.radius);
            buf.writeBoolean(entry.ballistic);
        }
    }

    private static MissileCatalogSyncPacket read(RegistryFriendlyByteBuf buf) {
        boolean authorized = buf.readBoolean();
        boolean sbw = buf.readBoolean();
        boolean active = buf.readBoolean();
        String warehouse = buf.readUtf(128);
        int supply = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid missile catalog size: " + count);
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUtf(64), buf.readUtf(128), buf.readUtf(160),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
                    buf.readVarInt(), buf.readFloat(), buf.readBoolean()));
        }
        return new MissileCatalogSyncPacket(authorized, sbw, active,
                warehouse, supply, List.copyOf(entries));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
