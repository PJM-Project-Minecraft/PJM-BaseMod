package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

/** Клиент → сервер: заспавнить технику {@code instanceId} на выбранной точке {@code index} (0-based). */
public record SpawnAtPointPacket(UUID instanceId, int index) implements CustomPacketPayload {

    public static final Type<SpawnAtPointPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "spawn_at_point"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnAtPointPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, SpawnAtPointPacket::instanceId,
                    ByteBufCodecs.VAR_INT, SpawnAtPointPacket::index,
                    SpawnAtPointPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
