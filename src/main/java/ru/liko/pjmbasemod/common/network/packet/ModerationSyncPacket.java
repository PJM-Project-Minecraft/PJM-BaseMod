package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.moderation.ModerationSnapshot;

/** Сервер → клиент: обновить открытый GUI модерации новым снимком (после действия). */
public record ModerationSyncPacket(ModerationSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<ModerationSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "moderation_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ModerationSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> ModerationSnapshot.write(buf, packet.snapshot()),
            buf -> new ModerationSyncPacket(ModerationSnapshot.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
