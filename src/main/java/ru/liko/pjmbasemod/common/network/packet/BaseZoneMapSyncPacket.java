package ru.liko.pjmbasemod.common.network.packet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.basezone.BaseZoneView;

/** S→C: все завершённые зоны базы для отображения на карте (owner-цвет уже разрешён на сервере). */
public record BaseZoneMapSyncPacket(List<BaseZoneView> zones) implements CustomPacketPayload {

    public static final Type<BaseZoneMapSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "basezone_map_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BaseZoneMapSyncPacket> STREAM_CODEC =
            StreamCodec.of(BaseZoneMapSyncPacket::write, BaseZoneMapSyncPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, BaseZoneMapSyncPacket p) {
        buf.writeVarInt(p.zones.size());
        for (BaseZoneView z : p.zones) {
            buf.writeUtf(z.displayName());
            buf.writeUtf(z.dimension());
            buf.writeUtf(z.owner());
            buf.writeInt(z.ownerColor());
            buf.writeInt(z.minX());
            buf.writeInt(z.minZ());
            buf.writeInt(z.maxX());
            buf.writeInt(z.maxZ());
        }
    }

    private static BaseZoneMapSyncPacket read(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<BaseZoneView> zones = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String displayName = buf.readUtf();
            String dimension = buf.readUtf();
            String owner = buf.readUtf();
            int ownerColor = buf.readInt();
            int minX = buf.readInt();
            int minZ = buf.readInt();
            int maxX = buf.readInt();
            int maxZ = buf.readInt();
            zones.add(new BaseZoneView(displayName, dimension, owner, ownerColor, minX, minZ, maxX, maxZ));
        }
        return new BaseZoneMapSyncPacket(List.copyOf(zones));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
