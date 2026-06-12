package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.customization.CustomizationType;

public record SelectCustomizationPacket(CustomizationType customizationType, String optionId) implements CustomPacketPayload {

    public static final Type<SelectCustomizationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "select_customization"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectCustomizationPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, p -> p.customizationType.getId(),
                    ByteBufCodecs.STRING_UTF8, SelectCustomizationPacket::optionId,
                    (type, id) -> new SelectCustomizationPacket(CustomizationType.byId(type), id)
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
