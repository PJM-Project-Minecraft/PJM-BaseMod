package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.ArrayList;
import java.util.List;

/** S→C: пул разрешённых скинов и текущий выбор локального игрока (для меню кастомизации). */
public record SkinSelectionSyncPacket(List<String> allowedSkins, String currentSkin)
        implements CustomPacketPayload {

    public static final Type<SkinSelectionSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "skin_selection_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkinSelectionSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.allowedSkins().size());
                        for (String skin : packet.allowedSkins()) {
                            buf.writeUtf(skin);
                        }
                        buf.writeUtf(packet.currentSkin() == null ? "" : packet.currentSkin());
                    },
                    buf -> {
                        int count = buf.readVarInt();
                        List<String> skins = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            skins.add(buf.readUtf());
                        }
                        return new SkinSelectionSyncPacket(skins, buf.readUtf());
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
