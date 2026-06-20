package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

public record SetFactionOrderPacket(String text, int ttlMinutes) implements CustomPacketPayload {

    public static final Type<SetFactionOrderPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "set_faction_order"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFactionOrderPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SetFactionOrderPacket::text,
                    ByteBufCodecs.VAR_INT, SetFactionOrderPacket::ttlMinutes,
                    SetFactionOrderPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
