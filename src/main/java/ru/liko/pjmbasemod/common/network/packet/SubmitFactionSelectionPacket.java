package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

public record SubmitFactionSelectionPacket(String teamId, String roleId) implements CustomPacketPayload {

    public static final Type<SubmitFactionSelectionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "submit_faction_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitFactionSelectionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SubmitFactionSelectionPacket::teamId,
                    ByteBufCodecs.STRING_UTF8, SubmitFactionSelectionPacket::roleId,
                    SubmitFactionSelectionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
