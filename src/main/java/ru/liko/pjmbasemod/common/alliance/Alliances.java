package ru.liko.pjmbasemod.common.alliance;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.common.faction.FactionCommanderService;
import ru.liko.pjmbasemod.common.logging.LogCategory;
import ru.liko.pjmbasemod.common.logging.PjmActionLogger;
import ru.liko.pjmbasemod.common.teams.Teams;

import javax.annotation.Nullable;

/**
 * Фасад союзов фракций. Единая точка ответа на вопрос «свои ли эти двое» —
 * {@link #friendly}: используется гейтом дружественного огня, зоной базы и точками захвата,
 * поэтому новое место, где нужен этот вопрос, обязано звать сюда, а не сравнивать teamId.
 *
 * <p>Союз даёт ровно три вещи: нет урона между союзниками, база союзника не убивает,
 * союзник не отбирает точки союзника. Склад, гараж, радейки и лимиты ролей остаются
 * строго внутрифракционными — иначе союз превращается в слияние фракций.</p>
 */
public final class Alliances {

    /** Сколько живёт предложение союза, минуты. */
    public static final int OFFER_TTL_MINUTES = 5;

    private Alliances() {}

    @Nullable
    public static String allyOf(@Nullable MinecraftServer server, @Nullable String teamId) {
        if (server == null || teamId == null || teamId.isBlank()) return null;
        return AllianceSavedData.get(server).allyOf(teamId);
    }

    /** Свои ли фракции: одна и та же либо состоящие в союзе. */
    public static boolean friendly(@Nullable MinecraftServer server, @Nullable String teamA, @Nullable String teamB) {
        if (teamA == null || teamB == null || teamA.isBlank() || teamB.isBlank()) return false;
        if (teamA.equalsIgnoreCase(teamB)) return true;
        String ally = allyOf(server, teamA);
        return ally != null && ally.equalsIgnoreCase(Teams.normalize(teamB));
    }

    /** Свои ли игроки. Игрок без команды не свой никому, включая другого бескомандного. */
    public static boolean friendly(@Nullable ServerPlayer a, @Nullable ServerPlayer b) {
        if (a == null || b == null || a.getServer() == null) return false;
        return friendly(a.getServer(), Teams.resolvePlayerTeamId(a), Teams.resolvePlayerTeamId(b));
    }

    /**
     * Игроки из <b>разных</b> фракций, состоящих в союзе. В отличие от {@link #friendly}
     * не считает своими сокомандников: тимкиллом внутри фракции заведует ванильный
     * {@code friendlyFire} scoreboard-команды, и союз в это правило не вмешивается.
     */
    public static boolean allied(@Nullable ServerPlayer a, @Nullable ServerPlayer b) {
        if (a == null || b == null || a.getServer() == null) return false;
        String teamA = Teams.resolvePlayerTeamId(a);
        String teamB = Teams.resolvePlayerTeamId(b);
        if (teamA == null || teamB == null || teamA.equalsIgnoreCase(teamB)) return false;
        return friendly(a.getServer(), teamA, teamB);
    }

    /**
     * Имя стороны для объявлений: «Союз A + B», если фракция в союзе, иначе имя фракции.
     * Захват точки и победа в кампании принадлежат союзу целиком, поэтому пишутся так.
     */
    public static String sideName(@Nullable MinecraftServer server, @Nullable String teamId) {
        if (server == null || teamId == null || teamId.isBlank()) return Teams.NEUTRAL_NAME;
        String ally = allyOf(server, teamId);
        String own = Teams.displayName(server, teamId);
        return ally == null ? own : "Союз " + own + " + " + Teams.displayName(server, ally);
    }

    // ---------------------------------------------------------------- заключение и разрыв

    /** Предложить союз другой фракции. {@code durationMinutes <= 0} — навсегда. */
    public static Result offer(MinecraftServer server, String fromTeam, String toTeam, long durationMinutes) {
        if (!Teams.isCombatTeam(toTeam)) return Result.fail("Такой фракции нет.");
        String to = Teams.normalize(toTeam);
        String from = Teams.normalize(fromTeam);
        if (from.equals(to)) return Result.fail("Нельзя заключить союз с самим собой.");

        AllianceSavedData data = AllianceSavedData.get(server);
        if (data.allianceOf(from) != null) {
            return Result.fail("Ваша фракция уже в союзе — сначала разорвите его: /pjm alliance break");
        }
        if (data.allianceOf(to) != null) {
            return Result.fail("Фракция " + Teams.displayName(server, to) + " уже состоит в союзе.");
        }

        // Встречное предложение = согласие: вторая сторона уже позвала, просто заключаем.
        AllianceSavedData.Offer incoming = data.offerTo(from);
        if (incoming != null && incoming.from().equals(to)) {
            return accept(server, from, to);
        }

        data.offer(from, to, durationMinutes, OFFER_TTL_MINUTES);
        announce(server, to, Component.literal("Фракция " + Teams.displayName(server, from)
                        + " предлагает союз " + durationText(durationMinutes)
                        + ". Ответ командира: /pjm alliance accept — есть " + OFFER_TTL_MINUTES + " мин.")
                .withStyle(ChatFormatting.GOLD));
        return Result.ok("Предложение союза отправлено фракции " + Teams.displayName(server, to)
                + " (" + durationText(durationMinutes) + ").");
    }

    /** Принять живое предложение, адресованное фракции {@code team}. */
    public static Result accept(MinecraftServer server, String team, @Nullable String fromTeamFilter) {
        AllianceSavedData data = AllianceSavedData.get(server);
        AllianceSavedData.Offer offer = data.offerTo(Teams.normalize(team));
        if (offer == null || (fromTeamFilter != null && !offer.from().equals(Teams.normalize(fromTeamFilter)))) {
            return Result.fail("Вам никто не предлагал союз.");
        }
        if (!data.form(offer.from(), offer.to(), offer.durationMinutes())) {
            return Result.fail("Одна из фракций уже состоит в союзе.");
        }
        String name = sideName(server, offer.from());
        Component message = Component.literal("Заключён " + name + " · " + durationText(offer.durationMinutes()))
                .withStyle(ChatFormatting.GREEN);
        announce(server, offer.from(), message);
        announce(server, offer.to(), message);
        PjmActionLogger.instance().logSubsystem(LogCategory.CAPTURE,
                "заключён союз " + offer.from() + " + " + offer.to() + " (" + durationText(offer.durationMinutes()) + ")");
        return Result.ok("Союз заключён.");
    }

    public static Result decline(MinecraftServer server, String team) {
        AllianceSavedData data = AllianceSavedData.get(server);
        AllianceSavedData.Offer offer = data.offerTo(Teams.normalize(team));
        if (offer == null) return Result.fail("Вам никто не предлагал союз.");
        data.revokeOffer(offer.from());
        announce(server, offer.from(), Component.literal("Фракция " + Teams.displayName(server, team)
                + " отклонила предложение союза.").withStyle(ChatFormatting.RED));
        return Result.ok("Предложение отклонено.");
    }

    /** Разрыв союза — односторонний и мгновенный. */
    public static Result dissolve(MinecraftServer server, String team) {
        String former = AllianceSavedData.get(server).dissolve(Teams.normalize(team));
        if (former == null) return Result.fail("Ваша фракция ни с кем не в союзе.");
        Component message = Component.literal("Союз " + Teams.displayName(server, team) + " + "
                + Teams.displayName(server, former) + " разорван.").withStyle(ChatFormatting.RED);
        announce(server, team, message);
        announce(server, former, message);
        PjmActionLogger.instance().logSubsystem(LogCategory.CAPTURE,
                "союз " + Teams.normalize(team) + " + " + former + " разорван");
        return Result.ok("Союз разорван.");
    }

    /** Истечение срочных союзов: снимает и уведомляет обе стороны. */
    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) return;
        for (AllianceSavedData.Alliance expired : AllianceSavedData.get(server).purgeExpired()) {
            Component message = Component.literal("Срок союза " + Teams.displayName(server, expired.teamA())
                    + " + " + Teams.displayName(server, expired.teamB()) + " истёк.")
                    .withStyle(ChatFormatting.RED);
            announce(server, expired.teamA(), message);
            announce(server, expired.teamB(), message);
            PjmActionLogger.instance().logSubsystem(LogCategory.CAPTURE,
                    "союз " + expired.teamA() + " + " + expired.teamB() + " истёк по сроку");
        }
    }

    /**
     * Фракция, от чьего имени игрок распоряжается союзами: командир своей фракции.
     * OP резолвится по своей команде, а фракцию может указать аргументом команды.
     */
    @Nullable
    public static String authorityTeam(ServerPlayer player) {
        String commanderTeam = FactionCommanderService.activeCommanderTeam(player);
        if (commanderTeam != null) return commanderTeam;
        return player.hasPermissions(2) ? Teams.resolvePlayerTeamId(player) : null;
    }

    public static String durationText(long durationMinutes) {
        if (durationMinutes <= 0) return "навсегда";
        if (durationMinutes % 60 == 0) return "на " + (durationMinutes / 60) + " ч";
        return "на " + durationMinutes + " мин";
    }

    private static void announce(MinecraftServer server, String teamId, Component message) {
        String team = Teams.normalize(teamId);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (team.equals(Teams.resolvePlayerTeamId(player))) player.sendSystemMessage(message);
        }
    }

    public record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message); }
    }
}
