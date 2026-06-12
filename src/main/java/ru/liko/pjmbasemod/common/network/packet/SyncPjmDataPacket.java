package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.chat.ChatMode;

import java.util.UUID;

public record SyncPjmDataPacket(UUID playerId, String chatMode)
        implements CustomPacketPayload {

    public static final Type<SyncPjmDataPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "sync_pjm_data"));

    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, SyncPjmDataPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUUID(p.playerId());
                        buf.writeUtf(p.chatMode());
                    },
                    buf -> new SyncPjmDataPacket(buf.readUUID(), buf.readUtf())
            );

    public ChatMode chatModeEnum() { return ChatMode.byId(chatMode); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
