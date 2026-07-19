package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.ArrayList;
import java.util.List;

/**
 * S→C: счёт недельной кампании для HUD. {@code secondsToEnd} — остаток до вайпа
 * (клиент досчитывает локально от момента получения, как приказ фракции).
 */
public record CampaignSyncPacket(
        boolean active,
        int secondsToEnd,
        List<TeamScore> scores
) implements CustomPacketPayload {

    public record TeamScore(String displayName, int color, long vp) {}

    public static final Type<CampaignSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "campaign_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CampaignSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.active());
                buf.writeInt(packet.secondsToEnd());
                buf.writeVarInt(packet.scores().size());
                for (TeamScore score : packet.scores()) {
                    buf.writeUtf(score.displayName());
                    buf.writeVarInt(score.color());
                    buf.writeVarLong(score.vp());
                }
            },
            buf -> {
                boolean active = buf.readBoolean();
                int secondsToEnd = buf.readInt();
                int count = buf.readVarInt();
                List<TeamScore> scores = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    scores.add(new TeamScore(buf.readUtf(), buf.readVarInt(), buf.readVarLong()));
                }
                return new CampaignSyncPacket(active, secondsToEnd, List.copyOf(scores));
            }
    );

    public static CampaignSyncPacket empty() {
        return new CampaignSyncPacket(false, 0, List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
