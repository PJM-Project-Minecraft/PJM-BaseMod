package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * S→C: серверные HUD-флаги конфига. Нужен, потому что клиентские оверлеи скрытия полосок
 * читаются на клиенте, а конфиг — серверный (на выделенном сервере клиент его не видит).
 */
public record HudConfigPacket(boolean disableHunger, boolean hideArmorBar) implements CustomPacketPayload {

    public static final Type<HudConfigPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "hud_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudConfigPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, HudConfigPacket::disableHunger,
                    ByteBufCodecs.BOOL, HudConfigPacket::hideArmorBar,
                    HudConfigPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
