package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сервер → клиент: открыть поверх экрана гаража меню выбора точки спавна.
 * Содержит id экземпляра техники и список точек терминала с пометкой занятости.
 */
public record SpawnPointOptionsPacket(UUID instanceId, List<PointOption> points) implements CustomPacketPayload {

    /** Одна точка спавна: индекс (0-based), подпись для отображения и признак свободности. */
    public record PointOption(int index, String label, boolean free) {}

    public static final Type<SpawnPointOptionsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "spawn_point_options"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnPointOptionsPacket> STREAM_CODEC = StreamCodec.of(
            SpawnPointOptionsPacket::write, SpawnPointOptionsPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, SpawnPointOptionsPacket packet) {
        buf.writeUUID(packet.instanceId());
        buf.writeVarInt(packet.points().size());
        for (PointOption option : packet.points()) {
            buf.writeVarInt(option.index());
            buf.writeUtf(option.label());
            buf.writeBoolean(option.free());
        }
    }

    private static SpawnPointOptionsPacket read(RegistryFriendlyByteBuf buf) {
        UUID instanceId = buf.readUUID();
        int count = buf.readVarInt();
        List<PointOption> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new PointOption(buf.readVarInt(), buf.readUtf(), buf.readBoolean()));
        }
        return new SpawnPointOptionsPacket(instanceId, List.copyOf(points));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
