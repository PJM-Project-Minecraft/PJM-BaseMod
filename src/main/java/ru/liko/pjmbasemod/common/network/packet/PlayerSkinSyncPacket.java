package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

/** S→C: скин конкретного игрока для рендера (рассылается всем). */
public record PlayerSkinSyncPacket(UUID playerId, String skinId) implements CustomPacketPayload {

    public static final Type<PlayerSkinSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "player_skin_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerSkinSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, packet -> packet.playerId().toString(),
                    ByteBufCodecs.STRING_UTF8, PlayerSkinSyncPacket::skinId,
                    (playerId, skinId) -> new PlayerSkinSyncPacket(UUID.fromString(playerId), skinId));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
