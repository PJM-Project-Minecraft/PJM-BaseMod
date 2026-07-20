package ru.liko.pjmbasemod.common.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * C→S: ответ на приглашение во фракцию — принять ({@code accept = true}) или отказаться.
 * Сервер перепроверяет само приглашение: клиент лишь сообщает решение.
 */
public record FactionInviteResponsePacket(String teamId, boolean accept) implements CustomPacketPayload {

    public static final Type<FactionInviteResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "faction_invite_response"));

    public static final StreamCodec<ByteBuf, FactionInviteResponsePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, FactionInviteResponsePacket::teamId,
                    ByteBufCodecs.BOOL, FactionInviteResponsePacket::accept,
                    FactionInviteResponsePacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
