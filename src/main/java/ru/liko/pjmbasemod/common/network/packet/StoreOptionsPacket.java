package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сервер → клиент: открыть поверх экрана гаража меню выбора техники для возврата в гараж.
 * Содержит список техники, найденной в зоне хранения текущего гаража и подходящей по типу.
 */
public record StoreOptionsPacket(List<Option> options) implements CustomPacketPayload {

    /** Одна единица техники, доступная для возврата: id сущности в мире + имя и тип для отображения. */
    public record Option(UUID entityId, String displayName, String entityType) {}

    public static final Type<StoreOptionsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "store_options"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StoreOptionsPacket> STREAM_CODEC = StreamCodec.of(
            StoreOptionsPacket::write, StoreOptionsPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, StoreOptionsPacket packet) {
        buf.writeVarInt(packet.options().size());
        for (Option option : packet.options()) {
            buf.writeUUID(option.entityId());
            buf.writeUtf(option.displayName());
            buf.writeUtf(option.entityType());
        }
    }

    private static StoreOptionsPacket read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Option> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            options.add(new Option(buf.readUUID(), buf.readUtf(), buf.readUtf()));
        }
        return new StoreOptionsPacket(List.copyOf(options));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
