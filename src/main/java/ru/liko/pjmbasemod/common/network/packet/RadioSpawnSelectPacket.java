package ru.liko.pjmbasemod.common.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

/** C→S: выбор радио-рюкзака как точки возрождения (перед нажатием respawn). */
public record RadioSpawnSelectPacket(UUID radioId) implements CustomPacketPayload {

    public static final Type<RadioSpawnSelectPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "radio_spawn_select"));

    public static final StreamCodec<ByteBuf, RadioSpawnSelectPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, RadioSpawnSelectPacket::radioId,
                    RadioSpawnSelectPacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
