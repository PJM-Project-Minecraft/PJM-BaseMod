package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.FactionInviteResponsePacket;
import ru.liko.pjmbasemod.common.network.packet.OpenFactionInvitePacket;

/**
 * Экран приглашения во фракцию — «контракт»: кто зовёт, куда, что игрок теряет,
 * сколько осталось времени, и две кнопки — принять или отказать.
 *
 * <p>В отличие от {@link FactionSelectionScreen} открывается независимо от текущей
 * фракции игрока, поэтому его можно закрыть (Esc = «решу позже»): приглашение
 * останется висеть до истечения TTL и предложится снова при следующем входе.</p>
 */
public final class FactionInviteScreen extends PjmBaseScreen {

    private static final int GUI_WIDTH = 300;
    private static final int GUI_HEIGHT = 168;
    private static final int HEADER_HEIGHT = 22;

    private static final int BTN_W = 122;
    private static final int BTN_H = 24;
    private static final int BTN_GAP = 12;

    private final OpenFactionInvitePacket invite;
    /** Момент открытия — от него локально тикает обратный отсчёт TTL. */
    private final long openedAtMs = System.currentTimeMillis();
    /** Ответ уже отправлен: блокирует повторные клики до закрытия экрана. */
    private boolean answered;

    private FactionInviteScreen(OpenFactionInvitePacket invite) {
        super(Component.translatable("gui.pjmbasemod.faction.invite.screen.title"), GUI_WIDTH, GUI_HEIGHT);
        this.invite = invite;
    }

    public static void open(OpenFactionInvitePacket invite) {
        Minecraft.getInstance().setScreen(new FactionInviteScreen(invite));
    }

    /** Секунд до истечения; 0 — приглашение бессрочное, отсчёт не показываем. */
    private int secondsLeft() {
        if (invite.expiresInSeconds() <= 0) return 0;
        long spent = (System.currentTimeMillis() - openedAtMs) / 1000L;
        return (int) Math.max(0L, invite.expiresInSeconds() - spent);
    }

    private int acceptX() {
        return guiLeft() + (GUI_WIDTH - (BTN_W * 2 + BTN_GAP)) / 2;
    }

    private int declineX() {
        return acceptX() + BTN_W + BTN_GAP;
    }

    private int buttonsY() {
        return guiTop() + GUI_HEIGHT - BTN_H - 16;
    }

    @Override
    protected void renderScaled(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = guiLeft();
        int top = guiTop();

        PjmGuiUtils.drawScreenPanel(g, left, top, GUI_WIDTH, GUI_HEIGHT, 0, HEADER_HEIGHT);
        g.drawString(font, getTitle(), left + 10, top + 7, PjmGuiUtils.TEXT_PRIMARY, false);

        int y = top + HEADER_HEIGHT + 14;

        // Фракция — крупно, её цветом.
        int teamColor = invite.teamColor() == 0 ? PjmGuiUtils.ACCENT : 0xFF000000 | invite.teamColor();
        String team = invite.teamName();
        g.pose().pushPose();
        g.pose().translate(left + GUI_WIDTH / 2f, y, 0);
        g.pose().scale(1.6f, 1.6f, 1f);
        PjmGuiUtils.drawOutlinedString(g, font, team, -font.width(team) / 2, 0, teamColor);
        g.pose().popPose();
        y += 24;

        if (!invite.inviterName().isEmpty()) {
            drawCentered(g, Component.translatable("gui.pjmbasemod.faction.invite.screen.from",
                    invite.inviterName()).getString(), left, y, PjmGuiUtils.TEXT_DIM);
            y += 14;
        }

        // Предупреждение о смене фракции — главный смысл экрана для того, кто уже воюет.
        if (!invite.currentTeamName().isEmpty()) {
            drawCentered(g, Component.translatable("gui.pjmbasemod.faction.invite.screen.leaving",
                    invite.currentTeamName()).getString(), left, y, PjmGuiUtils.TEXT_MUTED);
            y += 14;
        }

        int left_ = secondsLeft();
        if (invite.expiresInSeconds() > 0) {
            String timer = Component.translatable("gui.pjmbasemod.faction.invite.screen.expires",
                    left_ / 60, String.format("%02d", left_ % 60)).getString();
            drawCentered(g, timer, left, y, left_ <= 30 ? 0xFFD86A5E : PjmGuiUtils.TEXT_LABEL);
        }

        boolean enabled = !answered && !(invite.expiresInSeconds() > 0 && left_ <= 0);
        drawButton(g, acceptX(), buttonsY(), mouseX, mouseY,
                Component.translatable("gui.pjmbasemod.faction.invite.screen.accept").getString(),
                PjmGuiUtils.BTN_GREEN, PjmGuiUtils.BTN_GREEN_HOVER, enabled);
        drawButton(g, declineX(), buttonsY(), mouseX, mouseY,
                Component.translatable("gui.pjmbasemod.faction.invite.screen.decline").getString(),
                PjmGuiUtils.BTN_RED, PjmGuiUtils.BTN_RED_HOVER, enabled);
    }

    private void drawCentered(GuiGraphics g, String text, int left, int y, int color) {
        g.drawString(font, text, left + (GUI_WIDTH - font.width(text)) / 2, y, color, false);
    }

    private void drawButton(GuiGraphics g, int x, int y, int mouseX, int mouseY,
                            String label, int base, int hover, boolean enabled) {
        boolean hot = enabled && inside(mouseX, mouseY, x, y);
        g.fill(x, y, x + BTN_W, y + BTN_H, enabled ? (hot ? hover : base) : PjmGuiUtils.BTN_DISABLED);
        PjmGuiUtils.drawBorder(g, x, y, BTN_W, BTN_H, PjmGuiUtils.SCREEN_BORDER);
        int color = enabled ? PjmGuiUtils.TEXT_PRIMARY : PjmGuiUtils.TEXT_MUTED;
        g.drawString(font, label, x + (BTN_W - font.width(label)) / 2, y + (BTN_H - 8) / 2, color, false);
    }

    private boolean inside(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BTN_W && mouseY >= y && mouseY < y + BTN_H;
    }

    @Override
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        if (button != 0 || answered) return false;
        if (invite.expiresInSeconds() > 0 && secondsLeft() <= 0) return false;

        if (inside(mouseX, mouseY, acceptX(), buttonsY())) {
            respond(true);
            return true;
        }
        if (inside(mouseX, mouseY, declineX(), buttonsY())) {
            respond(false);
            return true;
        }
        return false;
    }

    private void respond(boolean accept) {
        answered = true;
        PjmUiSounds.playPress(Minecraft.getInstance().getSoundManager());
        PjmNetworking.sendToServer(new FactionInviteResponsePacket(invite.teamId(), accept));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
