package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/** C→S: выдать ({@code invite=true}) или отозвать приглашение в закрытую фракцию по нику. */
public record ManageFactionInvitePacket(String playerName, boolean invite) implements CustomPacketPayload {

    public static final Type<ManageFactionInvitePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "manage_faction_invite"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ManageFactionInvitePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ManageFactionInvitePacket::playerName,
                    ByteBufCodecs.BOOL, ManageFactionInvitePacket::invite,
                    ManageFactionInvitePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
