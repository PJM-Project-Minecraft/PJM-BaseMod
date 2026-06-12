package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.rank.RankSnapshot;

public record RankXpPacket(
        int delta,
        String reason,
        boolean rankChanged,
        boolean promoted,
        int xp,
        String rankId,
        String displayName,
        String shortName,
        String icon,
        int accentColor,
        int minXp,
        String nextDisplayName,
        int nextMinXp,
        boolean enabled,
        boolean showRankHud,
        boolean showXpPopups,
        boolean showTabPrefix
) implements CustomPacketPayload {

    public static final Type<RankXpPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "rank_xp"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RankXpPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.delta());
                buf.writeUtf(packet.reason());
                buf.writeBoolean(packet.rankChanged());
                buf.writeBoolean(packet.promoted());
                buf.writeVarInt(packet.xp());
                buf.writeUtf(packet.rankId());
                buf.writeUtf(packet.displayName());
                buf.writeUtf(packet.shortName());
                buf.writeUtf(packet.icon());
                buf.writeVarInt(packet.accentColor());
                buf.writeVarInt(packet.minXp());
                buf.writeUtf(packet.nextDisplayName());
                buf.writeVarInt(packet.nextMinXp());
                buf.writeBoolean(packet.enabled());
                buf.writeBoolean(packet.showRankHud());
                buf.writeBoolean(packet.showXpPopups());
                buf.writeBoolean(packet.showTabPrefix());
            },
            buf -> new RankXpPacket(
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean()
            )
    );

    public static RankXpPacket from(int delta, String reason, boolean rankChanged, boolean promoted, RankSnapshot snapshot) {
        return new RankXpPacket(
                delta,
                reason == null ? "" : reason,
                rankChanged,
                promoted,
                snapshot.xp(),
                snapshot.rankId(),
                snapshot.displayName(),
                snapshot.shortName(),
                snapshot.icon(),
                snapshot.accentColor(),
                snapshot.minXp(),
                snapshot.nextDisplayName(),
                snapshot.nextMinXp(),
                snapshot.enabled(),
                snapshot.showRankHud(),
                snapshot.showXpPopups(),
                snapshot.showTabPrefix()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
