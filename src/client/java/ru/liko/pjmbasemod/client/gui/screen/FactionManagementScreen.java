package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;
import ru.liko.pjmbasemod.common.faction.FactionManagementSnapshot;
import ru.liko.pjmbasemod.common.faction.FactionSelectionSnapshot;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.ManageFactionRolePacket;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class FactionManagementScreen extends PjmBaseScreen {

    private static final int GUI_WIDTH = 520;
    private static final int GUI_HEIGHT = 300;
    private static final int HEADER_HEIGHT = 24;
    private static final int SIDEBAR_WIDTH = 184;
    private static final int MEMBER_ROW_HEIGHT = 28;
    private static final int ROLE_ROW_HEIGHT = 28;

    private FactionManagementSnapshot snapshot;
    private int selectedMember;
    private int scroll;
    private float appear;

    public FactionManagementScreen(FactionManagementSnapshot snapshot) {
        super(Component.translatable("gui.pjmbasemod.faction.manage.title"), GUI_WIDTH, GUI_HEIGHT);
        this.snapshot = snapshot;
    }

    public static void open(FactionManagementSnapshot snapshot) {
        Minecraft.getInstance().setScreen(new FactionManagementScreen(snapshot));
    }

    public void updateSnapshot(FactionManagementSnapshot snapshot) {
        UUID previous = selectedMemberEntry() == null ? null : selectedMemberEntry().playerId();
        this.snapshot = snapshot;
        selectedMember = 0;
        if (previous != null) {
            for (int i = 0; i < snapshot.members().size(); i++) {
                if (previous.equals(snapshot.members().get(i).playerId())) {
                    selectedMember = i;
                    break;
                }
            }
        }
        clampScroll();
    }


    private int rowsVisible() {
        return Math.max(1, (GUI_HEIGHT - HEADER_HEIGHT - 20) / MEMBER_ROW_HEIGHT);
    }

    private void clampScroll() {
        int max = Math.max(0, snapshot.members().size() - rowsVisible());
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
        if (selectedMember >= snapshot.members().size()) selectedMember = Math.max(0, snapshot.members().size() - 1);
    }

    @Override
    public void tick() {
        super.tick();
        appear += (1.0F - appear) * 0.2F;
    }

    @Override
    protected boolean mouseScrolledScaled(int mouseX, int mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scroll -= (int) Math.signum(scrollY);
            clampScroll();
            return true;
        }
        return super.mouseScrolledScaled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

        float eased = easeOut(appear);
        int left = guiLeft();
        int top = guiTop() + Math.round((1.0F - eased) * 14.0F);
        int accent = 0xFF000000 | snapshot.teamColor();

        graphics.fill(left, top, left + GUI_WIDTH, top + GUI_HEIGHT, 0xF216161A);
        drawBorder(graphics, left, top, GUI_WIDTH, GUI_HEIGHT, 0xFF353540);
        graphics.fill(left, top, left + GUI_WIDTH, top + HEADER_HEIGHT, 0xFF1F1F26);
        graphics.fill(left, top + HEADER_HEIGHT, left + SIDEBAR_WIDTH, top + GUI_HEIGHT, 0xFF1A1A20);
        graphics.fill(left, top + HEADER_HEIGHT - 2, left + GUI_WIDTH, top + HEADER_HEIGHT, accent);

        graphics.drawString(font, getTitle(), left + 8, top + 8, 0xFFE8E8E8, false);
        graphics.drawString(font, ellipsize(snapshot.teamName(), 160), left + 180, top + 8, 0xFFD8D8D8, false);
        boolean closeHovered = mouseX >= left + GUI_WIDTH - 28 && mouseX <= left + GUI_WIDTH
                && mouseY >= top && mouseY <= top + HEADER_HEIGHT;
        graphics.drawString(font, "X", left + GUI_WIDTH - 18, top + 8,
                closeHovered ? 0xFFD06060 : 0xFFB05050, false);

        drawMembers(graphics, left, top, mouseX, mouseY);
        drawRolePanel(graphics, left, top, mouseX, mouseY);
    }

    private void drawMembers(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int x = left + 8;
        int y = top + HEADER_HEIGHT + 8;
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.members"), x, y, 0xFF9AA0A6, false);
        y += 14;

        List<FactionManagementSnapshot.MemberEntry> members = snapshot.members();
        int rows = rowsVisible();
        for (int i = scroll; i < members.size() && i < scroll + rows; i++) {
            FactionManagementSnapshot.MemberEntry member = members.get(i);
            boolean selected = i == selectedMember;
            boolean hovered = mouseX >= x && mouseX <= left + SIDEBAR_WIDTH - 8
                    && mouseY >= y && mouseY <= y + MEMBER_ROW_HEIGHT - 4;
            int bg = selected ? 0xFF35506E : hovered ? 0xFF2A2A33 : 0xFF222229;
            graphics.fill(x, y, left + SIDEBAR_WIDTH - 8, y + MEMBER_ROW_HEIGHT - 4, bg);
            String prefix = member.commander() ? "[KMD] " : "";
            graphics.drawString(font, ellipsize(prefix + member.name(), SIDEBAR_WIDTH - 34),
                    x + 8, y + 5, selected ? 0xFFFFFFFF : 0xFFE0E0E0, false);
            graphics.drawString(font, roleName(member.roleId()), x + 8, y + 16,
                    member.roleId().isBlank() ? 0xFF777777 : 0xFFD8B15F, false);
            y += MEMBER_ROW_HEIGHT;
        }

        if (members.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.no_members"),
                    x, y + 4, 0xFF777777, false);
        }
    }

    private void drawRolePanel(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        FactionManagementSnapshot.MemberEntry member = selectedMemberEntry();
        int x = left + SIDEBAR_WIDTH + 12;
        int y = top + HEADER_HEIGHT + 8;
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;

        if (member == null) {
            graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.select_member"),
                    x, y, 0xFF888888, false);
            return;
        }

        graphics.drawString(font, member.name(), x, y, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.current_role",
                roleName(member.roleId())), x, y + 12, 0xFF9AA0A6, false);
        y += 32;

        for (FactionSelectionSnapshot.RoleEntry role : snapshot.roles()) {
            boolean selected = role.id().equals(member.roleId());
            boolean available = role.available() || selected;
            boolean hovered = available && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROLE_ROW_HEIGHT - 4;
            int bg = !available ? 0xFF1D1D22 : selected ? 0xFF2B3E52 : hovered ? 0xFF2A2A33 : 0xFF222229;
            graphics.fill(x, y, x + w, y + ROLE_ROW_HEIGHT - 4, bg);
            graphics.fill(x, y, x + 3, y + ROLE_ROW_HEIGHT - 4, 0xFF000000 | role.color());
            graphics.drawString(font, ellipsize(role.displayName(), w - 116),
                    x + 10, y + 7, available ? 0xFFE8E8E8 : 0xFF777777, false);
            graphics.drawString(font, roleLimitText(role), x + w - 82, y + 7,
                    role.disabled() || role.full() ? 0xFFD8B15F : 0xFF9AA0A6, false);
            y += ROLE_ROW_HEIGHT;
        }

        int clearY = top + GUI_HEIGHT - 32;
        boolean clearHovered = mouseX >= x && mouseX <= x + w && mouseY >= clearY && mouseY <= clearY + 22;
        graphics.fill(x, clearY, x + w, clearY + 22, clearHovered ? 0xFF7A463E : 0xFF5A342E);
        graphics.drawCenteredString(font, Component.translatable("gui.pjmbasemod.faction.manage.clear_role"),
                x + w / 2, clearY + 7, 0xFFFFFFFF);
    }

    @Override
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        if (button != 0) return super.mouseClickedScaled(mouseX, mouseY, button);
        int left = guiLeft();
        int top = guiTop() + Math.round((1.0F - easeOut(appear)) * 14.0F);

        if (mouseX >= left + GUI_WIDTH - 28 && mouseX <= left + GUI_WIDTH
                && mouseY >= top && mouseY <= top + HEADER_HEIGHT) {
            PjmUiSounds.playClick();
            onClose();
            return true;
        }

        int memberX = left + 8;
        int memberY = top + HEADER_HEIGHT + 22;
        for (int i = scroll; i < snapshot.members().size() && i < scroll + rowsVisible(); i++) {
            int y = memberY + (i - scroll) * MEMBER_ROW_HEIGHT;
            if (mouseX >= memberX && mouseX <= left + SIDEBAR_WIDTH - 8
                    && mouseY >= y && mouseY <= y + MEMBER_ROW_HEIGHT - 4) {
                selectedMember = i;
                PjmUiSounds.playClick();
                return true;
            }
        }

        FactionManagementSnapshot.MemberEntry member = selectedMemberEntry();
        if (member == null) return super.mouseClickedScaled(mouseX, mouseY, button);

        int roleX = left + SIDEBAR_WIDTH + 12;
        int roleY = top + HEADER_HEIGHT + 40;
        int roleW = GUI_WIDTH - SIDEBAR_WIDTH - 24;
        for (FactionSelectionSnapshot.RoleEntry role : snapshot.roles()) {
            boolean selected = role.id().equals(member.roleId());
            boolean available = role.available() || selected;
            if (mouseX >= roleX && mouseX <= roleX + roleW && mouseY >= roleY && mouseY <= roleY + ROLE_ROW_HEIGHT - 4) {
                if (available) {
                    PjmUiSounds.playPress();
                    PjmNetworking.sendToServer(new ManageFactionRolePacket(member.playerId(), role.id()));
                }
                return true;
            }
            roleY += ROLE_ROW_HEIGHT;
        }

        int clearY = top + GUI_HEIGHT - 32;
        if (mouseX >= roleX && mouseX <= roleX + roleW && mouseY >= clearY && mouseY <= clearY + 22) {
            PjmUiSounds.playPress();
            PjmNetworking.sendToServer(new ManageFactionRolePacket(member.playerId(), ""));
            return true;
        }

        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    @Nullable
    private FactionManagementSnapshot.MemberEntry selectedMemberEntry() {
        List<FactionManagementSnapshot.MemberEntry> members = snapshot.members();
        return selectedMember >= 0 && selectedMember < members.size() ? members.get(selectedMember) : null;
    }

    private String roleName(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return Component.translatable("gui.pjmbasemod.radial.role_none").getString();
        }
        return Component.translatable("role.pjmbasemod." + roleId).getString();
    }

    private String roleLimitText(FactionSelectionSnapshot.RoleEntry role) {
        if (role.limit() < 0) {
            return Component.translatable("gui.pjmbasemod.faction.role_limit_unlimited", role.current()).getString();
        }
        return Component.translatable("gui.pjmbasemod.faction.role_limit", role.current(), role.limit()).getString();
    }

    private String ellipsize(String text, int maxWidth) {
        return PjmGuiUtils.ellipsize(font, text, maxWidth);
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        PjmGuiUtils.drawBorder(graphics, x, y, w, h, color);
    }

    private static float easeOut(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
