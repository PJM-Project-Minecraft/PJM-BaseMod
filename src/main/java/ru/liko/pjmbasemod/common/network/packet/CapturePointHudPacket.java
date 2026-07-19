package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * S→C: HUD-данные точки захвата, в которой стоит игрок. pointId="" — очистить HUD.
 * {@code locked} — точка закрыта цепочкой для команды игрока (сначала нужен order-сосед).
 */
public record CapturePointHudPacket(
        String pointId,
        String pointName,
        String ownerTeamName,
        int ownerColor,
        String captureTeamName,
        int captureColor,
        int progressPercent,
        boolean neutralizing,
        boolean capturing,
        boolean locked
) implements CustomPacketPayload {

    public static final Type<CapturePointHudPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "capturepoint_hud"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CapturePointHudPacket> STREAM_CODEC =
            StreamCodec.of(CapturePointHudPacket::write, CapturePointHudPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, CapturePointHudPacket p) {
        buf.writeUtf(p.pointId);
        buf.writeUtf(p.pointName);
        buf.writeUtf(p.ownerTeamName);
        buf.writeVarInt(p.ownerColor);
        buf.writeUtf(p.captureTeamName);
        buf.writeVarInt(p.captureColor);
        buf.writeVarInt(p.progressPercent);
        buf.writeBoolean(p.neutralizing);
        buf.writeBoolean(p.capturing);
        buf.writeBoolean(p.locked);
    }

    private static CapturePointHudPacket read(RegistryFriendlyByteBuf buf) {
        return new CapturePointHudPacket(
                buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readVarInt(),
                buf.readUtf(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static CapturePointHudPacket empty() {
        return new CapturePointHudPacket("", "", "", 0, "", 0, 0, false, false, false);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
