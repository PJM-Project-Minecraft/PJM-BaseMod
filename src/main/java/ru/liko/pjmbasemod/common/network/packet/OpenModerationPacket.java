package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.moderation.ModerationSnapshot;

/** Сервер → клиент: открыть GUI модерации с переданным снимком состояния. */
public record OpenModerationPacket(ModerationSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<OpenModerationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "open_moderation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenModerationPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> ModerationSnapshot.write(buf, packet.snapshot()),
            buf -> new OpenModerationPacket(ModerationSnapshot.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
