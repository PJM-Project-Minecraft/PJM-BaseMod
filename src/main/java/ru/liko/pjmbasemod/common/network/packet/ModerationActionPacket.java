package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.moderation.PunishmentType;

import java.util.UUID;

/**
 * Клиент → сервер: универсальный модераторский экшен из GUI.
 * {@code action}: {@code "apply"} — наложить, {@code "revoke"} — снять.
 * Права по типу действия проверяет сервер.
 */
public record ModerationActionPacket(PunishmentType punishment, String action, UUID targetId,
                                     long durationMs, String reason) implements CustomPacketPayload {

    public static final Type<ModerationActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "moderation_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ModerationActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeEnum(p.punishment());
                buf.writeUtf(p.action());
                buf.writeUUID(p.targetId());
                buf.writeLong(p.durationMs());
                buf.writeUtf(p.reason());
            },
            buf -> new ModerationActionPacket(
                    buf.readEnum(PunishmentType.class),
                    buf.readUtf(),
                    buf.readUUID(),
                    buf.readLong(),
                    buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
