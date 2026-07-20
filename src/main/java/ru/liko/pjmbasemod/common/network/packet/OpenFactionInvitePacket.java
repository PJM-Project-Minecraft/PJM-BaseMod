package ru.liko.pjmbasemod.common.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * S→C: открыть экран приглашения во фракцию («контракт») с выбором принять/отказать.
 *
 * @param teamId          id фракции-отправителя (для ответного пакета)
 * @param teamName        отображаемое имя фракции
 * @param teamColor       цвет фракции (RGB, 0 — цвета нет)
 * @param inviterName     ник пригласившего; пусто, если приглашение доставлено при входе
 * @param expiresInSeconds сколько секунд осталось; 0 — бессрочное
 * @param currentTeamName текущая фракция игрока; пусто — игрок ещё без фракции
 */
public record OpenFactionInvitePacket(String teamId, String teamName, int teamColor,
                                      String inviterName, int expiresInSeconds,
                                      String currentTeamName) implements CustomPacketPayload {

    public static final Type<OpenFactionInvitePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "open_faction_invite"));

    public static final StreamCodec<ByteBuf, OpenFactionInvitePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OpenFactionInvitePacket::teamId,
                    ByteBufCodecs.STRING_UTF8, OpenFactionInvitePacket::teamName,
                    ByteBufCodecs.INT, OpenFactionInvitePacket::teamColor,
                    ByteBufCodecs.STRING_UTF8, OpenFactionInvitePacket::inviterName,
                    ByteBufCodecs.VAR_INT, OpenFactionInvitePacket::expiresInSeconds,
                    ByteBufCodecs.STRING_UTF8, OpenFactionInvitePacket::currentTeamName,
                    OpenFactionInvitePacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
