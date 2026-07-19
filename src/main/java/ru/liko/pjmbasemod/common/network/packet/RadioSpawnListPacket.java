package ru.liko.pjmbasemod.common.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.List;
import java.util.UUID;

/**
 * S→C: список развёрнутых радио-рюкзаков команды погибшего — варианты
 * возрождения на экране смерти. Пустой список очищает варианты.
 */
public record RadioSpawnListPacket(List<Entry> entries) implements CustomPacketPayload {

    /** @param cooldownSeconds секунд до готовности рации на момент смерти; 0 — доступна сразу. */
    public record Entry(UUID id, String owner, BlockPos pos, int cooldownSeconds) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Entry::id,
                ByteBufCodecs.STRING_UTF8, Entry::owner,
                BlockPos.STREAM_CODEC, Entry::pos,
                ByteBufCodecs.VAR_INT, Entry::cooldownSeconds,
                Entry::new
        );
    }

    public static final Type<RadioSpawnListPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "radio_spawn_list"));

    public static final StreamCodec<ByteBuf, RadioSpawnListPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), RadioSpawnListPacket::entries,
                    RadioSpawnListPacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
