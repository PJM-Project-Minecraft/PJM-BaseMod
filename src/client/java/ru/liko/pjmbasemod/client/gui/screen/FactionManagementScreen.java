package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;
import ru.liko.pjmbasemod.common.faction.DeputyPermission;
import ru.liko.pjmbasemod.common.faction.FactionManagementSnapshot;
import ru.liko.pjmbasemod.common.faction.FactionSelectionSnapshot;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.ManageFactionDeputyPacket;
import ru.liko.pjmbasemod.common.network.packet.ManageFactionRolePacket;
import ru.liko.pjmbasemod.common.network.packet.SetFactionOrderPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FactionManagementScreen extends PjmBaseScreen {

    private static final int GUI_WIDTH = 520;
    private static final int GUI_HEIGHT = 300;
    private static final int HEADER_HEIGHT = 24;
    private static final int SIDEBAR_WIDTH = 184;
    private static final int MEMBER_ROW_HEIGHT = 28;
    private static final int ROLE_ROW_HEIGHT = 28;
    private static final int TAB_HEIGHT = 18;
    private static final int ORDER_MAX_LEN = 120;
    private static final int[] TTL_PRESETS = {0, 15, 30, 60, 120, 240}; // 0 = бессрочно (минуты)

    private enum Tab { ROLE, DEPUTY, ORDER }

    private FactionManagementSnapshot snapshot;
    private int selectedMember;
    private int scroll;
    private float appear;

    private Tab activeTab;
    private String orderInput = "";
    private boolean orderFocused;
    private int ttlIndex = 2; // default 30 мин

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
        ensureTab();
    }

    @Override
    protected void init() {
        super.init();
        ensureTab();
    }

    private List<Tab> availableTabs() {
        List<Tab> tabs = new ArrayList<>();
        if (snapshot.viewerCanAssignRoles()) tabs.add(Tab.ROLE);
        if (snapshot.viewerCanManageDeputies()) tabs.add(Tab.DEPUTY);
        if (snapshot.viewerCanSetOrder()) tabs.add(Tab.ORDER);
        return tabs;
    }

    private void ensureTab() {
        List<Tab> tabs = availableTabs();
        if (tabs.isEmpty()) {
            activeTab = null;
            return;
        }
        if (activeTab == null || !tabs.contains(activeTab)) {
            activeTab = tabs.get(0);
        }
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
        drawTabs(graphics, left, top, mouseX, mouseY);

        if (activeTab == Tab.ROLE) {
            drawRolePanel(graphics, left, top, mouseX, mouseY);
        } else if (activeTab == Tab.DEPUTY) {
            drawDeputyPanel(graphics, left, top, mouseX, mouseY);
        } else if (activeTab == Tab.ORDER) {
            drawOrderPanel(graphics, left, top, mouseX, mouseY);
        }
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
            String prefix = member.commander() ? "[КМД] " : member.deputy() ? "[ЗАМ] " : "";
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

    private void drawTabs(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<Tab> tabs = availableTabs();
        if (tabs.isEmpty()) return;
        int x = left + SIDEBAR_WIDTH + 12;
        int y = top + HEADER_HEIGHT + 6;
        int panelW = GUI_WIDTH - SIDEBAR_WIDTH - 24;
        int tabW = panelW / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tx = x + i * tabW;
            boolean current = tab == activeTab;
            boolean hovered = mouseX >= tx && mouseX <= tx + tabW - 2 && mouseY >= y && mouseY <= y + TAB_HEIGHT;
            int bg = current ? 0xFF35506E : hovered ? 0xFF2A2A33 : 0xFF222229;
            graphics.fill(tx, y, tx + tabW - 2, y + TAB_HEIGHT, bg);
            graphics.drawCenteredString(font, tabTitle(tab), tx + (tabW - 2) / 2, y + 5,
                    current ? 0xFFFFFFFF : 0xFFCCCCCC);
        }
    }

    private Component tabTitle(Tab tab) {
        return switch (tab) {
            case ROLE -> Component.translatable("gui.pjmbasemod.faction.manage.tab.role");
            case DEPUTY -> Component.translatable("gui.pjmbasemod.faction.manage.tab.deputy");
            case ORDER -> Component.translatable("gui.pjmbasemod.faction.manage.tab.order");
        };
    }

    private int contentTop(int top) {
        return top + HEADER_HEIGHT + 6 + TAB_HEIGHT + 8;
    }

    private void drawRolePanel(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        FactionManagementSnapshot.MemberEntry member = selectedMemberEntry();
        int x = left + SIDEBAR_WIDTH + 12;
        int y = contentTop(top);
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;

        if (member == null) {
            graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.select_member"),
                    x, y, 0xFF888888, false);
            return;
        }

        graphics.drawString(font, member.name(), x, y, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.current_role",
                roleName(member.roleId())), x, y + 12, 0xFF9AA0A6, false);
        y += 30;

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

    private void drawDeputyPanel(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        FactionManagementSnapshot.MemberEntry member = selectedMemberEntry();
        int x = left + SIDEBAR_WIDTH + 12;
        int y = contentTop(top);
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;

        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.deputy.count",
                snapshot.deputyCount(), snapshot.maxDeputies()), x, y, 0xFF9AA0A6, false);
        y += 18;

        if (member == null) {
            graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.select_member"),
                    x, y, 0xFF888888, false);
            return;
        }

        graphics.drawString(font, member.name(), x, y, 0xFFFFFFFF, false);
        y += 18;

        // Переключатель «Назначить замом»
        boolean isDeputy = member.deputy();
        boolean toggleHovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 22;
        int toggleBg = isDeputy ? 0xFF2E5E3A : toggleHovered ? 0xFF2A2A33 : 0xFF222229;
        graphics.fill(x, y, x + w, y + 22, toggleBg);
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.manage.deputy.toggle"), x + 10, y + 7,
                0xFFE8E8E8, false);
        graphics.drawString(font, isDeputy ? "ON" : "OFF", x + w - 30, y + 7, isDeputy ? 0xFF7CD68A : 0xFF888888, false);
        y += 30;

        // Чекбоксы прав (активны только если зам)
        DeputyPermission[] perms = DeputyPermission.values();
        for (DeputyPermission perm : perms) {
            boolean checked = DeputyPermission.has(member.deputyPerms(), perm);
            boolean enabled = isDeputy;
            boolean hovered = enabled && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 20;
            int bg = hovered ? 0xFF2A2A33 : 0xFF1E1E24;
            graphics.fill(x, y, x + w, y + 20, bg);
            int box = enabled ? (checked ? 0xFF7CD68A : 0xFF555560) : 0xFF333338;
            graphics.fill(x + 6, y + 5, x + 16, y + 15, box);
            graphics.drawString(font, permTitle(perm), x + 24, y + 6,
                    enabled ? 0xFFE8E8E8 : 0xFF666666, false);
            y += 24;
        }
    }

    private Component permTitle(DeputyPermission perm) {
        return switch (perm) {
            case ASSIGN_ROLES -> Component.translatable("gui.pjmbasemod.faction.manage.deputy.perm.assign_roles");
            case SET_ORDER -> Component.translatable("gui.pjmbasemod.faction.manage.deputy.perm.set_order");
            case OPEN_GUI -> Component.translatable("gui.pjmbasemod.faction.manage.deputy.perm.open_gui");
        };
    }

    private void drawOrderPanel(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int x = left + SIDEBAR_WIDTH + 12;
        int y = contentTop(top);
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;

        // Текущий приказ
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.order.current"), x, y, 0xFF9AA0A6, false);
        y += 12;
        if (snapshot.orderText().isBlank()) {
            graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.order.none"), x, y, 0xFF777777, false);
        } else {
            graphics.drawString(font, ellipsize(snapshot.orderText(), w), x, y, 0xFFE8E8E8, false);
            y += 11;
            String meta = Component.translatable("gui.pjmbasemod.faction.order.author", snapshot.orderAuthor()).getString();
            if (snapshot.orderSecondsRemaining() >= 0) {
                meta += "  " + Component.translatable("gui.pjmbasemod.faction.order.remaining",
                        snapshot.orderSecondsRemaining()).getString();
            } else {
                meta += "  " + Component.translatable("gui.pjmbasemod.faction.order.remaining_permanent").getString();
            }
            graphics.drawString(font, meta, x, y, 0xFF888888, false);
        }
        y += 22;

        // Поле ввода
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.order.title"), x, y, 0xFF9AA0A6, false);
        y += 12;
        int boxH = 22;
        graphics.fill(x, y, x + w, y + boxH, orderFocused ? 0xFF2A3550 : 0xFF222229);
        drawBorder(graphics, x, y, w, boxH, orderFocused ? 0xFF4A6A9E : 0xFF353540);
        String shown = orderInput;
        boolean caret = orderFocused && (System.currentTimeMillis() / 500L) % 2 == 0;
        String display = ellipsize(shown.isEmpty() && !orderFocused
                ? Component.translatable("gui.pjmbasemod.faction.order.placeholder").getString() : shown, w - 12);
        int textColor = shown.isEmpty() && !orderFocused ? 0xFF666666 : 0xFFE8E8E8;
        graphics.drawString(font, display + (caret ? "_" : ""), x + 6, y + 7, textColor, false);
        y += boxH + 8;

        // TTL-переключатель
        int ttlW = w / 2 - 4;
        boolean ttlHovered = mouseX >= x && mouseX <= x + ttlW && mouseY >= y && mouseY <= y + 22;
        graphics.fill(x, y, x + ttlW, y + 22, ttlHovered ? 0xFF2A2A33 : 0xFF222229);
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.order.ttl", ttlLabel()),
                x + 8, y + 7, 0xFFE8E8E8, false);

        // Кнопка «Отправить»
        int sendX = x + ttlW + 8;
        int sendW = w - ttlW - 8;
        boolean sendHovered = mouseX >= sendX && mouseX <= sendX + sendW && mouseY >= y && mouseY <= y + 22;
        graphics.fill(sendX, y, sendX + sendW, y + 22, sendHovered ? 0xFF2E5E3A : 0xFF274D31);
        graphics.drawCenteredString(font, Component.translatable("gui.pjmbasemod.faction.order.send"),
                sendX + sendW / 2, y + 7, 0xFFFFFFFF);
        y += 30;

        // Кнопка «Снять»
        boolean clearHovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 22;
        graphics.fill(x, y, x + w, y + 22, clearHovered ? 0xFF7A463E : 0xFF5A342E);
        graphics.drawCenteredString(font, Component.translatable("gui.pjmbasemod.faction.order.clear"),
                x + w / 2, y + 7, 0xFFFFFFFF);
    }

    private String ttlLabel() {
        int ttl = TTL_PRESETS[ttlIndex];
        return ttl <= 0
                ? Component.translatable("gui.pjmbasemod.faction.order.ttl_permanent").getString()
                : Component.translatable("gui.pjmbasemod.faction.order.ttl_minutes", ttl).getString();
    }

    @Override
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        if (button != 0) return super.mouseClickedScaled(mouseX, mouseY, button);
        int left = guiLeft();
        int top = guiTop() + Math.round((1.0F - easeOut(appear)) * 14.0F);

        // Закрытие
        if (mouseX >= left + GUI_WIDTH - 28 && mouseX <= left + GUI_WIDTH
                && mouseY >= top && mouseY <= top + HEADER_HEIGHT) {
            PjmUiSounds.playClick();
            onClose();
            return true;
        }

        // Список членов
        int memberX = left + 8;
        int memberY = top + HEADER_HEIGHT + 22;
        for (int i = scroll; i < snapshot.members().size() && i < scroll + rowsVisible(); i++) {
            int y = memberY + (i - scroll) * MEMBER_ROW_HEIGHT;
            if (mouseX >= memberX && mouseX <= left + SIDEBAR_WIDTH - 8
                    && mouseY >= y && mouseY <= y + MEMBER_ROW_HEIGHT - 4) {
                selectedMember = i;
                orderFocused = false;
                PjmUiSounds.playClick();
                return true;
            }
        }

        // Табы
        List<Tab> tabs = availableTabs();
        int tabX = left + SIDEBAR_WIDTH + 12;
        int tabY = top + HEADER_HEIGHT + 6;
        int panelW = GUI_WIDTH - SIDEBAR_WIDTH - 24;
        if (!tabs.isEmpty()) {
            int tabW = panelW / tabs.size();
            for (int i = 0; i < tabs.size(); i++) {
                int tx = tabX + i * tabW;
                if (mouseX >= tx && mouseX <= tx + tabW - 2 && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT) {
                    activeTab = tabs.get(i);
                    orderFocused = false;
                    PjmUiSounds.playClick();
                    return true;
                }
            }
        }

        if (activeTab == Tab.ROLE) return roleClick(left, top, mouseX, mouseY);
        if (activeTab == Tab.DEPUTY) return deputyClick(left, top, mouseX, mouseY);
        if (activeTab == Tab.ORDER) return orderClick(left, top, mouseX, mouseY);
        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    private boolean roleClick(int left, int top, int mouseX, int mouseY) {
        FactionManagementSnapshot.MemberEntry member = selectedMemberEntry();
        if (member == null) return true;
        int x = left + SIDEBAR_WIDTH + 12;
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;
        int y = contentTop(top) + 30;
        for (FactionSelectionSnapshot.RoleEntry role : snapshot.roles()) {
            boolean selected = role.id().equals(member.roleId());
            boolean available = role.available() || selected;
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROLE_ROW_HEIGHT - 4) {
                if (available) {
                    PjmUiSounds.playPress();
                    PjmNetworking.sendToServer(new ManageFactionRolePacket(member.playerId(), role.id()));
                }
                return true;
            }
            y += ROLE_ROW_HEIGHT;
        }
        int clearY = top + GUI_HEIGHT - 32;
        if (mouseX >= x && mouseX <= x + w && mouseY >= clearY && mouseY <= clearY + 22) {
            PjmUiSounds.playPress();
            PjmNetworking.sendToServer(new ManageFactionRolePacket(member.playerId(), ""));
            return true;
        }
        return true;
    }

    private boolean deputyClick(int left, int top, int mouseX, int mouseY) {
        FactionManagementSnapshot.MemberEntry member = selectedMemberEntry();
        if (member == null) return true;
        int x = left + SIDEBAR_WIDTH + 12;
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;
        int y = contentTop(top) + 18 + 18; // count + name

        // Toggle
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 22) {
            boolean newDeputy = !member.deputy();
            int perms = newDeputy ? member.deputyPerms() : 0;
            PjmUiSounds.playPress();
            PjmNetworking.sendToServer(new ManageFactionDeputyPacket(member.playerId(), newDeputy, perms));
            return true;
        }
        y += 30;

        // Чекбоксы прав
        if (member.deputy()) {
            for (DeputyPermission perm : DeputyPermission.values()) {
                if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 20) {
                    int perms = member.deputyPerms() ^ perm.bit();
                    PjmUiSounds.playClick();
                    PjmNetworking.sendToServer(new ManageFactionDeputyPacket(member.playerId(), true,
                            DeputyPermission.sanitize(perms)));
                    return true;
                }
                y += 24;
            }
        }
        return true;
    }

    private boolean orderClick(int left, int top, int mouseX, int mouseY) {
        int x = left + SIDEBAR_WIDTH + 12;
        int w = GUI_WIDTH - SIDEBAR_WIDTH - 24;
        int y = contentTop(top);
        // высота блока «текущий приказ»
        y += 12 + (snapshot.orderText().isBlank() ? 11 : 22) + 22;
        // заголовок поля
        y += 12;
        int boxH = 22;
        // Поле ввода → фокус
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + boxH) {
            orderFocused = true;
            PjmUiSounds.playClick();
            return true;
        }
        orderFocused = false;
        y += boxH + 8;

        int ttlW = w / 2 - 4;
        if (mouseX >= x && mouseX <= x + ttlW && mouseY >= y && mouseY <= y + 22) {
            ttlIndex = (ttlIndex + 1) % TTL_PRESETS.length;
            PjmUiSounds.playClick();
            return true;
        }
        int sendX = x + ttlW + 8;
        int sendW = w - ttlW - 8;
        if (mouseX >= sendX && mouseX <= sendX + sendW && mouseY >= y && mouseY <= y + 22) {
            sendOrder();
            return true;
        }
        y += 30;
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 22) {
            PjmUiSounds.playPress();
            PjmNetworking.sendToServer(new SetFactionOrderPacket("", 0));
            return true;
        }
        return true;
    }

    private void sendOrder() {
        if (orderInput.isBlank()) return;
        PjmUiSounds.playPress();
        PjmNetworking.sendToServer(new SetFactionOrderPacket(orderInput.trim(), TTL_PRESETS[ttlIndex]));
        orderInput = "";
        orderFocused = false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (orderFocused && activeTab == Tab.ORDER) {
            if (chr >= ' ' && orderInput.length() < ORDER_MAX_LEN) {
                orderInput += chr;
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (orderFocused && activeTab == Tab.ORDER) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!orderInput.isEmpty()) orderInput = orderInput.substring(0, orderInput.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                sendOrder();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                orderFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
