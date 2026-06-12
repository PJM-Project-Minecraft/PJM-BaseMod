package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.chat.ChatMode;

public record ChangeChatModePacket(ChatMode mode) implements CustomPacketPayload {

    public static final Type<ChangeChatModePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "change_chat_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChangeChatModePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, p -> p.mode.getKey(),
                    s -> new ChangeChatModePacket(ChatMode.byId(s))
            );

    public static ChangeChatModePacket setMode(ChatMode mode) {
        return new ChangeChatModePacket(mode);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
