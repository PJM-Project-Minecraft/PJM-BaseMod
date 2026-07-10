package ru.liko.pjmbasemod.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;
import ru.liko.pjmbasemod.common.faction.FactionSelectionSnapshot;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.SubmitFactionSelectionPacket;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Экран выбора фракции и боевой роли при первом заходе. Дизайн выдержан в едином стиле с остальными
 * меню мода (Warehouse / TacticalMainMenu): ванильный блюр-фон, карточки с hover-анимацией и левой
 * акцент-линией, сглаженные иконки, оверлей-замок для недоступных ролей.
 */
public class FactionSelectionScreen extends PjmBaseScreen {

    private static final int GUI_WIDTH = 520;
    private static final int GUI_HEIGHT = 320;
    private static final int HEADER_HEIGHT = 28;
    private static final int SIDEBAR_WIDTH = 168;
    private static final int TEAM_ROW_HEIGHT = 34;
    private static final int ROLE_ROW_HEIGHT = 32;
    private static final int CONFIRM_HEIGHT = 26;

    // Цвета берутся из PjmGuiUtils (amber-tactical палитра)
    private static final int COLOR_SCRIM         = PjmGuiUtils.SCREEN_SCRIM;
    private static final int COLOR_PANEL         = PjmGuiUtils.SCREEN_BG;
    private static final int COLOR_BORDER        = PjmGuiUtils.SCREEN_BORDER;
    private static final int COLOR_HEADER        = PjmGuiUtils.SCREEN_HEADER;
    private static final int COLOR_SIDEBAR       = PjmGuiUtils.SCREEN_SIDEBAR;
    private static final int COLOR_ROW           = PjmGuiUtils.SCREEN_ROW;
    private static final int COLOR_ROW_HOVER     = PjmGuiUtils.SCREEN_ROW_HOVER;
    private static final int COLOR_ROW_LOCKED    = PjmGuiUtils.SCREEN_ROW_LOCKED;
    private static final int COLOR_SELECT        = PjmGuiUtils.SCREEN_SELECT;
    private static final int COLOR_ROLE_SELECT   = PjmGuiUtils.SCREEN_SELECT;
    private static final int COLOR_GOLD          = PjmGuiUtils.TEXT_GOLD;
    private static final int COLOR_LABEL         = PjmGuiUtils.TEXT_LABEL;
    private static final int COLOR_TEXT          = PjmGuiUtils.TEXT_PRIMARY;
    private static final int COLOR_TEXT_DIM      = PjmGuiUtils.TEXT_DIM;
    private static final int COLOR_TEXT_MUTED    = PjmGuiUtils.TEXT_MUTED;
    private static final int COLOR_CONFIRM       = PjmGuiUtils.BTN_GREEN;
    private static final int COLOR_CONFIRM_HOVER = PjmGuiUtils.BTN_GREEN_HOVER;
    private static final int COLOR_CONFIRM_DISABLED = PjmGuiUtils.BTN_DISABLED;

    private static final ResourceLocation ROLE_ICON = ResourceLocation.fromNamespaceAndPath(
            Pjmbasemod.MODID, "textures/icon/class.png");
    private static final ResourceLocation LOCK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Pjmbasemod.MODID, "textures/icon/lock.png");

    private FactionSelectionSnapshot snapshot;
    private int selectedTeam;
    @Nullable
    private String selectedRole;
    private float appear;
    private boolean submitted;
    private int roleScroll;

    // Состояния hover-анимаций (lerp к 0/1)
    private float[] teamAnim = new float[0];
    private final Map<String, Float> roleAnim = new HashMap<>();
    private float confirmAnim;

    // Для FPS-независимых hover-анимаций: время предыдущего кадра и текущий шаг сглаживания.
    private long lastFrameNanos;
    private float animStep = 0.3F;

    public FactionSelectionScreen(FactionSelectionSnapshot snapshot) {
        super(Component.translatable("gui.pjmbasemod.faction.selection.title"), GUI_WIDTH, GUI_HEIGHT);
        this.snapshot = snapshot;
        initSelection();
    }

    public static void open(FactionSelectionSnapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FactionSelectionScreen screen) {
            screen.updateSnapshot(snapshot);
            return;
        }
        mc.setScreen(new FactionSelectionScreen(snapshot));
    }

    public void updateSnapshot(FactionSelectionSnapshot snapshot) {
        String teamId = selectedTeamEntry() == null ? snapshot.currentTeam() : selectedTeamEntry().id();
        String roleId = selectedRole;
        this.snapshot = snapshot;
        selectTeam(teamId);
        if (roleId != null && roleEntry(roleId) != null) {
            selectedRole = roleId;
        } else {
            selectDefaultRole();
        }
        clampScroll();
    }

    private void initSelection() {
        teamAnim = new float[snapshot.teams().size()];
        selectTeam(snapshot.currentTeam());
        selectDefaultRole();
    }


    private int contentLeft(int left) {
        return left + SIDEBAR_WIDTH + 14;
    }

    private int contentWidth() {
        return GUI_WIDTH - SIDEBAR_WIDTH - 28;
    }

    private int rolesTop(int top) {
        return top + HEADER_HEIGHT + 26;
    }

    private int rolesBottom(int top) {
        return top + GUI_HEIGHT - CONFIRM_HEIGHT - 18;
    }

    private int rolesVisible(int top) {
        return Math.max(1, (rolesBottom(top) - rolesTop(top)) / ROLE_ROW_HEIGHT);
    }

    @Override
    public void tick() {
        super.tick();
        appear += (1.0F - appear) * 0.18F;
    }

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // FPS-независимый шаг hover-анимаций: коэффициент сглаживания зависит от реального
        // времени кадра, а не от частоты кадров. При ~60 FPS поведение совпадает со старым 0.3.
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 1f / 60f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        dt = Mth.clamp(dt, 0f, 0.1f); // защита от скачков (лаг/пауза)
        animStep = 1f - (float) Math.exp(-dt * 21.3f); // 21.3 ≈ соответствует 0.3 при 60 FPS

        // Затемнение поверх блюра
        graphics.fill(0, 0, vWidth(), vHeight(), COLOR_SCRIM);

        float eased = easeOut(appear);
        int alpha = Math.round(255 * eased);
        int left = guiLeft();
        int top = guiTop() + Math.round((1.0F - eased) * 18.0F);

        // Корпус + обводка
        graphics.fill(left, top, left + GUI_WIDTH, top + GUI_HEIGHT, withAlpha(COLOR_PANEL, Math.round(206 * eased)));
        drawBorder(graphics, left, top, GUI_WIDTH, GUI_HEIGHT, withAlpha(COLOR_BORDER, alpha));

        // Хедер + цветная акцент-полоса выбранной фракции
        FactionSelectionSnapshot.TeamEntry team = selectedTeamEntry();
        int accent = team == null ? PjmGuiUtils.ACCENT : 0xFF000000 | team.color();
        graphics.fill(left, top, left + GUI_WIDTH, top + HEADER_HEIGHT, withAlpha(COLOR_HEADER, alpha));
        graphics.fill(left, top + HEADER_HEIGHT - 2, left + GUI_WIDTH, top + HEADER_HEIGHT, withAlpha(accent, Math.round(220 * eased)));

        graphics.drawString(font, getTitle(), left + 10, top + 10, COLOR_TEXT, false);
        Component required = Component.translatable("gui.pjmbasemod.faction.selection.required");
        graphics.drawString(font, required, left + GUI_WIDTH - font.width(required) - 12, top + 10, COLOR_GOLD, false);

        // Сайдбар фракций
        graphics.fill(left, top + HEADER_HEIGHT, left + SIDEBAR_WIDTH, top + GUI_HEIGHT, withAlpha(COLOR_SIDEBAR, alpha));

        drawTeams(graphics, left, top, mouseX, mouseY);
        drawRoles(graphics, left, top, mouseX, mouseY);
        drawConfirm(graphics, left, top, mouseX, mouseY);
    }

    private void drawTeams(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int x = left + 10;
        int y = top + HEADER_HEIGHT + 8;
        int rowW = SIDEBAR_WIDTH - 20;
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.selection.teams"), x, y, COLOR_LABEL, false);
        y += 14;

        if (teamAnim.length != snapshot.teams().size()) {
            teamAnim = new float[snapshot.teams().size()];
        }

        for (int i = 0; i < snapshot.teams().size(); i++) {
            FactionSelectionSnapshot.TeamEntry team = snapshot.teams().get(i);
            boolean selected = i == selectedTeam;
            boolean hovered = inside(mouseX, mouseY, x, y, rowW, TEAM_ROW_HEIGHT - 4);
            teamAnim[i] = lerp(teamAnim[i], selected || hovered ? 1.0F : 0.0F);

            int teamColor = 0xFF000000 | team.color();
            int bg = selected ? COLOR_SELECT : lerpColor(COLOR_ROW, COLOR_ROW_HOVER, teamAnim[i]);
            graphics.fill(x, y, x + rowW, y + TEAM_ROW_HEIGHT - 4, bg);
            // Левая цветная акцент-линия с яркостью по hover
            int lineW = 3 + Math.round(teamAnim[i] * 1.0F);
            graphics.fill(x, y, x + lineW, y + TEAM_ROW_HEIGHT - 4, teamColor);
            if (selected) {
                graphics.renderOutline(x, y, rowW, TEAM_ROW_HEIGHT - 4, withAlpha(teamColor, 200));
            }

            int textX = x + 12 + Math.round(teamAnim[i] * 3.0F);
            graphics.drawString(font, ellipsize(team.displayName(), rowW - 22), textX, y + 6,
                    selected ? 0xFFFFFFFF : COLOR_TEXT_DIM, false);
            graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.selection.roles_count",
                    team.roles().size()), textX, y + 17, COLOR_LABEL, false);
            y += TEAM_ROW_HEIGHT;
        }
    }

    private void drawRoles(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        FactionSelectionSnapshot.TeamEntry team = selectedTeamEntry();
        int x = contentLeft(left);
        int w = contentWidth();
        int labelY = top + HEADER_HEIGHT + 8;
        graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.selection.roles"), x, labelY, COLOR_LABEL, false);

        if (team == null || team.roles().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.pjmbasemod.faction.selection.no_roles"),
                    x, rolesTop(top) + 8, COLOR_TEXT_MUTED, false);
            return;
        }

        List<FactionSelectionSnapshot.RoleEntry> roles = team.roles();
        int rows = rolesVisible(top);
        int y = rolesTop(top);
        for (int i = roleScroll; i < roles.size() && i < roleScroll + rows; i++) {
            FactionSelectionSnapshot.RoleEntry role = roles.get(i);
            boolean selected = role.id().equals(selectedRole);
            boolean current = team.id().equals(snapshot.currentTeam()) && role.id().equals(snapshot.currentRole());
            boolean available = role.available() || current;
            boolean hovered = available && inside(mouseX, mouseY, x, y, w, ROLE_ROW_HEIGHT - 4);

            float anim = roleAnim.getOrDefault(role.id(), 0.0F);
            anim = lerp(anim, selected || hovered ? 1.0F : 0.0F);
            roleAnim.put(role.id(), anim);

            int roleColor = 0xFF000000 | role.color();
            int bg = !available ? COLOR_ROW_LOCKED
                    : selected ? COLOR_ROLE_SELECT
                    : lerpColor(COLOR_ROW, COLOR_ROW_HOVER, anim);
            graphics.fill(x, y, x + w, y + ROLE_ROW_HEIGHT - 4, bg);
            graphics.fill(x, y, x + 3 + Math.round(anim), y + ROLE_ROW_HEIGHT - 4,
                    available ? roleColor : withAlpha(roleColor, 120));
            if (selected) {
                graphics.renderOutline(x, y, w, ROLE_ROW_HEIGHT - 4, withAlpha(roleColor, 200));
            }

            // Иконка класса (+ замок для недоступной роли)
            int iconY = y + (ROLE_ROW_HEIGHT - 4 - 16) / 2;
            drawSmoothIcon(graphics, ROLE_ICON, x + 8, iconY, 16, 16, available ? 1.0F : 0.4F);
            if (!available) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200.0F);
                drawSmoothIcon(graphics, LOCK_TEXTURE, x + 13, iconY + 5, 10, 10, 1.0F);
                graphics.pose().popPose();
            }

            int textX = x + 32 + Math.round(anim * 2.0F);
            int nameColor = !available ? COLOR_TEXT_MUTED : selected ? 0xFFFFFFFF : COLOR_TEXT;
            graphics.drawString(font, ellipsize(role.displayName(), w - 130), textX, y + 8, nameColor, false);

            String limit = roleLimitText(role);
            graphics.drawString(font, limit, x + w - font.width(limit) - 8, y + 8,
                    role.disabled() || role.full() ? COLOR_GOLD : COLOR_LABEL, false);
            y += ROLE_ROW_HEIGHT;
        }

        drawScrollbar(graphics, x + w + 2, rolesTop(top), rolesBottom(top) - rolesTop(top), roles.size(), rows);
    }

    private void drawConfirm(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int x = contentLeft(left);
        int w = contentWidth();
        int y = top + GUI_HEIGHT - CONFIRM_HEIGHT - 10;
        boolean enabled = confirmEnabled();
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, CONFIRM_HEIGHT);
        confirmAnim = lerp(confirmAnim, hovered ? 1.0F : 0.0F);

        int color = !enabled ? COLOR_CONFIRM_DISABLED : lerpColor(COLOR_CONFIRM, COLOR_CONFIRM_HOVER, confirmAnim);
        graphics.fill(x, y, x + w, y + CONFIRM_HEIGHT, color);
        if (enabled) {
            graphics.renderOutline(x, y, w, CONFIRM_HEIGHT, withAlpha(0xFF6CCB78, Math.round(60 + confirmAnim * 140)));
        }
        graphics.drawCenteredString(font, Component.translatable(submitted
                        ? "gui.pjmbasemod.faction.selection.submitted"
                        : "gui.pjmbasemod.faction.selection.confirm"),
                x + w / 2, y + 9, enabled ? 0xFFFFFFFF : COLOR_TEXT_MUTED);
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y, int height, int total, int visible) {
        PjmGuiUtils.drawScrollbar(graphics, x, y, height, total, visible, roleScroll);
    }

    @Override
    protected boolean mouseScrolledScaled(int mouseX, int mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            roleScroll -= (int) Math.signum(scrollY);
            clampScroll();
            return true;
        }
        return super.mouseScrolledScaled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        if (button != 0) return super.mouseClickedScaled(mouseX, mouseY, button);

        int left = guiLeft();
        int top = guiTop() + Math.round((1.0F - easeOut(appear)) * 18.0F);

        // Фракции
        int teamX = left + 10;
        int teamY = top + HEADER_HEIGHT + 22;
        int teamW = SIDEBAR_WIDTH - 20;
        for (int i = 0; i < snapshot.teams().size(); i++) {
            int y = teamY + i * TEAM_ROW_HEIGHT;
            if (inside((double)mouseX, (double)mouseY, teamX, y, teamW, TEAM_ROW_HEIGHT - 4)) {
                if (selectedTeam != i) {
                    selectedTeam = i;
                    selectDefaultRole();
                    roleScroll = 0;
                    clampScroll();
                    PjmUiSounds.playClick();
                }
                return true;
            }
        }

        // Роли
        FactionSelectionSnapshot.TeamEntry team = selectedTeamEntry();
        if (team != null && !team.roles().isEmpty()) {
            int roleX = contentLeft(left);
            int roleW = contentWidth();
            int rows = rolesVisible(top);
            int y = rolesTop(top);
            List<FactionSelectionSnapshot.RoleEntry> roles = team.roles();
            for (int i = roleScroll; i < roles.size() && i < roleScroll + rows; i++) {
                FactionSelectionSnapshot.RoleEntry role = roles.get(i);
                boolean current = team.id().equals(snapshot.currentTeam()) && role.id().equals(snapshot.currentRole());
                if (inside((double)mouseX, (double)mouseY, roleX, y, roleW, ROLE_ROW_HEIGHT - 4)) {
                    if (role.available() || current) {
                        selectedRole = role.id();
                        PjmUiSounds.playClick();
                    }
                    return true;
                }
                y += ROLE_ROW_HEIGHT;
            }
        }

        // Подтверждение
        int confirmX = contentLeft(left);
        int confirmW = contentWidth();
        int confirmY = top + GUI_HEIGHT - CONFIRM_HEIGHT - 10;
        if (confirmEnabled() && inside((double)mouseX, (double)mouseY, confirmX, confirmY, confirmW, CONFIRM_HEIGHT)) {
            submitSelection();
            return true;
        }

        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    private void submitSelection() {
        FactionSelectionSnapshot.TeamEntry team = selectedTeamEntry();
        if (team == null || selectedRole == null) return;
        submitted = true;
        PjmUiSounds.playPress();
        PjmNetworking.sendToServer(new SubmitFactionSelectionPacket(team.id(), selectedRole));
        Minecraft.getInstance().setScreen(null);
    }

    private void clampScroll() {
        FactionSelectionSnapshot.TeamEntry team = selectedTeamEntry();
        int total = team == null ? 0 : team.roles().size();
        int visible = rolesVisible(guiTop());
        int max = Math.max(0, total - visible);
        roleScroll = Mth.clamp(roleScroll, 0, max);
    }

    private boolean confirmEnabled() {
        FactionSelectionSnapshot.TeamEntry team = selectedTeamEntry();
        FactionSelectionSnapshot.RoleEntry role = selectedRole == null ? null : roleEntry(selectedRole);
        if (team == null || role == null || submitted) return false;
        boolean current = team.id().equals(snapshot.currentTeam()) && role.id().equals(snapshot.currentRole());
        return role.available() || current;
    }

    @Nullable
    private FactionSelectionSnapshot.TeamEntry selectedTeamEntry() {
        List<FactionSelectionSnapshot.TeamEntry> teams = snapshot.teams();
        return selectedTeam >= 0 && selectedTeam < teams.size() ? teams.get(selectedTeam) : null;
    }

    @Nullable
    private FactionSelectionSnapshot.RoleEntry roleEntry(String roleId) {
        FactionSelectionSnapshot.TeamEntry team = selectedTeamEntry();
        if (team == null || roleId == null) return null;
        for (FactionSelectionSnapshot.RoleEntry role : team.roles()) {
            if (role.id().equals(roleId)) return role;
        }
        return null;
    }

    private void selectTeam(String teamId) {
        selectedTeam = 0;
        if (teamId == null || teamId.isBlank()) return;
        for (int i = 0; i < snapshot.teams().size(); i++) {
            if (snapshot.teams().get(i).id().equals(teamId)) {
                selectedTeam = i;
                return;
            }
        }
    }

    private void selectDefaultRole() {
        selectedRole = null;
        FactionSelectionSnapshot.TeamEntry team = selectedTeamEntry();
        if (team == null) return;
        if (team.id().equals(snapshot.currentTeam()) && roleEntry(snapshot.currentRole()) != null) {
            selectedRole = snapshot.currentRole();
            return;
        }
        for (FactionSelectionSnapshot.RoleEntry role : team.roles()) {
            if (role.available()) {
                selectedRole = role.id();
                return;
            }
        }
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

    private void drawSmoothIcon(GuiGraphics graphics, ResourceLocation icon, int x, int y, int w, int h, float alpha) {
        PjmGuiUtils.drawSmoothIcon(graphics, icon, x, y, w, h, alpha);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        PjmGuiUtils.drawBorder(graphics, x, y, w, h, color);
    }

    private static int withAlpha(int color, int alpha) {
        return PjmGuiUtils.withAlpha(color, alpha);
    }

    private float lerp(float current, float target) {
        float next = current + (target - current) * animStep;
        return Math.abs(target - next) < 0.001F ? target : next;
    }

    private static int lerpColor(int from, int to, float t) {
        return PjmGuiUtils.lerpColor(from, to, t);
    }

    private static float easeOut(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        if (!snapshot.required() || submitted) {
            super.onClose();
        }
    }
}
