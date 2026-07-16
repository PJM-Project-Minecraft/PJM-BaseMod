package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * S→C: кинематографичный экран смерти. {@code message} — ванильное сообщение о смерти,
 * {@code itemId} — id предмета-оружия для иконки (или пусто), {@code vehicleId} — id
 * entity техники SuperbWarfare для 3D-модели (или пусто). Оба пусты — показывается только текст.
 */
public record DeathScreenPacket(Component message, String itemId, String vehicleId)
        implements CustomPacketPayload {

    public static final Type<DeathScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "death_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeathScreenPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.STREAM_CODEC, DeathScreenPacket::message,
                    ByteBufCodecs.STRING_UTF8, DeathScreenPacket::itemId,
                    ByteBufCodecs.STRING_UTF8, DeathScreenPacket::vehicleId,
                    DeathScreenPacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
