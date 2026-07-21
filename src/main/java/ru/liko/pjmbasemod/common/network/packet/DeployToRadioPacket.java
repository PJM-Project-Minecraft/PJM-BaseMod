package ru.liko.pjmbasemod.common.network.packet;

import java.util.UUID;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** C→S: запрос десанта из своей базы к носителю рации {@code carrierId}. Сервер валидирует. */
public record DeployToRadioPacket(UUID carrierId) implements CustomPacketPayload {

    public static final Type<DeployToRadioPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "deploy_to_radio"));

    public static final StreamCodec<ByteBuf, DeployToRadioPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, DeployToRadioPacket::carrierId,
            DeployToRadioPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
