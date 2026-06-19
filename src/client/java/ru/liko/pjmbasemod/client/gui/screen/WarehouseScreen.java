package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.gui.GuiItemIcons;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.DepositItemPacket;
import ru.liko.pjmbasemod.common.network.packet.WithdrawItemPacket;
import ru.liko.pjmbasemod.common.warehouse.WarehousePoolCategory;
import ru.liko.pjmbasemod.common.warehouse.WarehouseSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * GUI склада (NPC-кладовщик): слева — вкладки display-категорий, сверху — баланс очков
 * по пулам, в центре — список выдаваемых предметов со стоимостью и кнопкой получения.
 */
public class WarehouseScreen extends PjmBaseScreen {

    private static final int GUI_WIDTH = 600;
    private static final int GUI_HEIGHT = 360;
    private static final int SIDEBAR_WIDTH = 150;
    private static final int HEADER_HEIGHT = 24;
    private static final int POOL_BAR_HEIGHT = 34;
    private static final int ROW_HEIGHT = 34;
    private static final int BUTTON_WIDTH = 88;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int CATEGORY_ROW_HEIGHT = 22;
    private static final int CATEGORY_ROW_STEP = 26;
    /** Категории, которые показываются всегда (даже если предметов нет): постоянные разделы магазина. */
    private static final List<String> ALWAYS_SHOWN_CATEGORIES = List.of("attachment", "grenade", "equipment");
    /** Канонический порядок вкладок-категорий в сайдбаре; неизвестные — в конец по алфавиту. */
    private static final List<String> CATEGORY_ORDER = List.of(
            "weapon", "ammo", "attachment", "grenade", "equipment", "medicine", "food", "raw", "vehicle", "special");
    private static final ResourceLocation LOCK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Pjmbasemod.MODID, "textures/icon/lock.png");
    private static final ResourceLocation CLOSE_ICON = ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/close.png");
    private static final ResourceLocation POINTS_ICON = ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/points.png");
    private static final ResourceLocation LOAD_ICON = ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/load.png");
    private static final ResourceLocation UNLOAD_ICON = ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/unload.png");

    private void drawSmoothIcon(GuiGraphics graphics, ResourceLocation icon, int x, int y, int width, int height) {
        PjmGuiUtils.drawSmoothIcon(graphics, icon, x, y, width, height);
    }

    private ResourceLocation getCategoryIcon(String category) {
        // Иконки категорий убраны: остаётся только class.png у категории special.
        return switch (category) {
            case "special" -> ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/class.png");
            default -> null;
        };
    }

    private WarehouseSnapshot snapshot;
    private final List<String> categories = new ArrayList<>();
    private int selectedCategory;
    private int scroll;

    public WarehouseScreen(WarehouseSnapshot snapshot) {
        super(Component.translatable("gui.pjmbasemod.warehouse.title"), GUI_WIDTH, GUI_HEIGHT);
        this.snapshot = snapshot;
        rebuildCategories();
    }

    public static void open(WarehouseSnapshot snapshot) {
        Minecraft.getInstance().setScreen(new WarehouseScreen(snapshot));
    }

    public void updateSnapshot(WarehouseSnapshot snapshot) {
        this.snapshot = snapshot;
        rebuildCategories();
        clampScroll();
    }

    private void rebuildCategories() {
        String previous = selectedCategory >= 0 && selectedCategory < categories.size()
                ? categories.get(selectedCategory) : null;
        Set<String> distinct = new LinkedHashSet<>();
        for (WarehouseSnapshot.ItemEntry item : snapshot.items()) {
            distinct.add(item.displayCategory());
        }
        distinct.addAll(ALWAYS_SHOWN_CATEGORIES);
        List<String> ordered = new ArrayList<>(distinct);
        ordered.sort((a, b) -> {
            int ia = CATEGORY_ORDER.indexOf(a);
            int ib = CATEGORY_ORDER.indexOf(b);
            if (ia < 0) ia = Integer.MAX_VALUE;
            if (ib < 0) ib = Integer.MAX_VALUE;
            return ia != ib ? Integer.compare(ia, ib) : a.compareTo(b);
        });
        categories.clear();
        categories.addAll(ordered);
        selectedCategory = previous == null ? 0 : Math.max(0, categories.indexOf(previous));
        if (selectedCategory >= categories.size()) selectedCategory = 0;
    }

    private List<WarehouseSnapshot.ItemEntry> currentItems() {
        if (categories.isEmpty()) return List.of();
        String category = categories.get(selectedCategory);
        List<WarehouseSnapshot.ItemEntry> result = new ArrayList<>();
        for (WarehouseSnapshot.ItemEntry item : snapshot.items()) {
            if (item.displayCategory().equals(category)) result.add(item);
        }
        return result;
    }

    private int listTop() { return guiTop() + HEADER_HEIGHT + POOL_BAR_HEIGHT + 6; }

    private int rowsVisible() {
        int listHeight = guiTop() + GUI_HEIGHT - 8 - listTop();
        return Math.max(1, listHeight / ROW_HEIGHT);
    }

    // Единый источник зон кнопок строки — используется и в рендере, и в обработке клика.
    private int contentLeft()  { return guiLeft() + SIDEBAR_WIDTH + 8; }
    private int contentWidth() { return GUI_WIDTH - SIDEBAR_WIDTH - 16; }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    /** Зона вкладки категории в сайдбаре — единый источник для рендера и клика. */
    private Rect categoryRect(int index) {
        int x = guiLeft() + 6;
        int y = guiTop() + HEADER_HEIGHT + 6 + index * CATEGORY_ROW_STEP;
        return new Rect(x, y, SIDEBAR_WIDTH - 12, CATEGORY_ROW_HEIGHT);
    }

    private Rect withdrawRect(int rowY) {
        int buttonY = rowY + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;
        int x = contentLeft() + contentWidth() - BUTTON_WIDTH - 4;
        return new Rect(x, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private Rect depositRect(int rowY) {
        int buttonY = rowY + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;
        int x = contentLeft() + contentWidth() - BUTTON_WIDTH - 4 - BUTTON_GAP - BUTTON_WIDTH;
        return new Rect(x, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private void clampScroll() {
        int max = Math.max(0, currentItems().size() - rowsVisible());
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    @Override
    protected void init() {
        super.init();
        clampScroll();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return mouseScrolledScaled(vMouseX(mouseX), vMouseY(mouseY), scrollX, scrollY);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClickedScaled(vMouseX(mouseX), vMouseY(mouseY), button);
    }

    @Override
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        if (button != 0) return super.mouseClickedScaled(mouseX, mouseY, button);

        int left = guiLeft();
        int top = guiTop();

        // Закрыть
        if (mouseX >= left + GUI_WIDTH - 24 && mouseX <= left + GUI_WIDTH && mouseY >= top && mouseY <= top + HEADER_HEIGHT) {
            PjmUiSounds.playClick();
            this.onClose();
            return true;
        }

        // Вкладки категорий
        for (int i = 0; i < categories.size(); i++) {
            if (categoryRect(i).contains(mouseX, mouseY)) {
                if (selectedCategory != i) {
                    PjmUiSounds.playClick();
                    selectedCategory = i;
                    scroll = 0;
                    clampScroll();
                }
                return true;
            }
        }

        // Кнопки получения
        int rows = rowsVisible();
        List<WarehouseSnapshot.ItemEntry> items = currentItems();
        int y = listTop();
        for (int i = scroll; i < items.size() && i < scroll + rows; i++) {
            WarehouseSnapshot.ItemEntry item = items.get(i);

            if (withdrawRect(y).contains(mouseX, mouseY) && snapshot.canWithdraw() && item.affordable()
                    && item.roleAllowed() && item.rankAllowed()) {
                PjmUiSounds.playClick();
                PjmNetworking.sendToServer(new WithdrawItemPacket(item.defId(), 1));
                return true;
            }

            if (depositRect(y).contains(mouseX, mouseY) && item.depositable() && item.inventoryCount() > 0) {
                int amount = hasShiftDown() ? Math.min(64, item.inventoryCount()) : 1;
                PjmUiSounds.playClick();
                PjmNetworking.sendToServer(new DepositItemPacket(item.defId(), amount));
                return true;
            }
            y += ROW_HEIGHT;
        }

        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = guiLeft();
        int top = guiTop();

        // Фон + обводка
        graphics.fill(left, top, left + GUI_WIDTH, top + GUI_HEIGHT, 0xF216161A);
        graphics.fill(left - 1, top - 1, left + GUI_WIDTH + 1, top, 0xFF353540);
        graphics.fill(left - 1, top + GUI_HEIGHT, left + GUI_WIDTH + 1, top + GUI_HEIGHT + 1, 0xFF353540);
        graphics.fill(left - 1, top, left, top + GUI_HEIGHT, 0xFF353540);
        graphics.fill(left + GUI_WIDTH, top, left + GUI_WIDTH + 1, top + GUI_HEIGHT, 0xFF353540);

        // Заголовок
        graphics.fill(left, top, left + GUI_WIDTH, top + HEADER_HEIGHT, 0xFF1F1F26);
        graphics.drawString(this.font, getTitle(), left + 8, top + 7, 0xFFE8E8E8, false);
        drawSmoothIcon(graphics, CLOSE_ICON, left + GUI_WIDTH - 19, top + 4, 14, 14);

        // Личный лимит (анти-«пылесос»): остаток/потолок справа в хедере. max=0 — лимит выключен.
        if (snapshot.personalBudgetMax() > 0) {
            String budgetText = Component.translatable("gui.pjmbasemod.warehouse.budget",
                    snapshot.personalBudget(), snapshot.personalBudgetMax()).getString();
            boolean low = snapshot.personalBudget() <= 0;
            int budgetW = this.font.width(budgetText);
            graphics.drawString(this.font, budgetText, left + GUI_WIDTH - 30 - budgetW, top + 7,
                    low ? 0xFFD16C6C : 0xFF6FC36F, false);
        }

        // Сайдбар
        graphics.fill(left, top + HEADER_HEIGHT, left + SIDEBAR_WIDTH, top + GUI_HEIGHT, 0xFF1A1A20);
        for (int i = 0; i < categories.size(); i++) {
            Rect r = categoryRect(i);
            boolean selected = i == selectedCategory;
            graphics.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), selected ? 0xFF35506E : 0xFF26262E);

            ResourceLocation icon = getCategoryIcon(categories.get(i));
            String label = Component.translatable("gui.pjmbasemod.warehouse.category." + categories.get(i)).getString();
            int textY = r.y() + (r.h() - 8) / 2;
            if (icon != null) {
                drawSmoothIcon(graphics, icon, r.x() + 5, r.y() + (r.h() - 14) / 2, 14, 14);
                graphics.drawString(this.font, label, r.x() + 24, textY, selected ? 0xFFFFFFFF : 0xFFB8B8B8, false);
            } else {
                graphics.drawString(this.font, label, r.x() + 8, textY, selected ? 0xFFFFFFFF : 0xFFB8B8B8, false);
            }
        }

        // Панель очков по пулам
        int poolY = top + HEADER_HEIGHT + 4;
        int poolX = left + SIDEBAR_WIDTH + 8;
        
        drawSmoothIcon(graphics, POINTS_ICON, poolX, poolY + 1, 14, 14);
        
        int currentX = poolX + 18;
        int currentY = poolY + 4;
        int maxRight = left + GUI_WIDTH - 8;
        
        for (WarehousePoolCategory pool : WarehousePoolCategory.values()) {
            int value = snapshot.points().getOrDefault(pool, 0);
            String name = Component.translatable(pool.translationKey()).getString();
            String text = name + ": " + value;
            int textWidth = this.font.width(text);
            
            if (currentX > poolX + 18 && currentX + textWidth > maxRight) {
                currentX = poolX + 18;
                currentY += 12;
            }
            
            graphics.drawString(this.font, text, currentX, currentY, 0xFFD8B15F, false);
            currentX += textWidth + 12;
        }

        // Список предметов
        int contentLeft = left + SIDEBAR_WIDTH + 8;
        int contentWidth = GUI_WIDTH - SIDEBAR_WIDTH - 16;
        int rows = rowsVisible();
        List<WarehouseSnapshot.ItemEntry> items = currentItems();
        int y = listTop();
        for (int i = scroll; i < items.size() && i < scroll + rows; i++) {
            WarehouseSnapshot.ItemEntry item = items.get(i);
            boolean roleLocked = !item.roleAllowed();
            boolean rankLocked = item.roleAllowed() && !item.rankAllowed();
            boolean locked = roleLocked || rankLocked;
            graphics.fill(contentLeft, y, contentLeft + contentWidth, y + ROW_HEIGHT - 3,
                    locked ? 0xFF1D1D22 : 0xFF222229);

            int itemY = y + (ROW_HEIGHT - 3 - 16) / 2;
            ItemStack icon = GuiItemIcons.stackFor(item.itemId());
            graphics.renderItem(icon, contentLeft + 4, itemY);

            // Имя: своё из конфига, иначе — локализованное имя предмета (клиентская сторона).
            String itemName = item.displayName().isBlank()
                    ? icon.getHoverName().getString()
                    : item.displayName();
            if (locked) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200.0F);
                graphics.fill(contentLeft + 4, itemY, contentLeft + 20, itemY + 16, 0xAA000000);
                graphics.pose().translate(contentLeft + 7, itemY + 3, 0);
                graphics.pose().scale(10.0F / 16.0F, 10.0F / 16.0F, 1.0F);
                graphics.blit(LOCK_TEXTURE, 0, 0, 0, 0, 16, 16, 16, 16);
                graphics.pose().popPose();
            }

            graphics.drawString(this.font, itemName, contentLeft + 26, y + 4,
                    locked ? 0xFF9A9A9A : 0xFFE8E8E8, false);
            String cost = Component.translatable("gui.pjmbasemod.warehouse.cost", item.pointCost()).getString();
            if (item.quantity() > 1) {
                cost += " ×" + item.quantity();
            }
            if (item.depositable()) {
                cost += "  " + Component.translatable("gui.pjmbasemod.warehouse.refund", item.refundValue()).getString();
            }

            int buttonY = y + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;
            Rect withdraw = withdrawRect(y);
            Rect deposit = depositRect(y);
            int withdrawX = withdraw.x();
            int depositX = deposit.x();
            if (roleLocked) {
                cost = Component.translatable("gui.pjmbasemod.role.required",
                        roleNames(item.allowedRoles())).getString();
            } else if (rankLocked) {
                cost = Component.translatable("gui.pjmbasemod.rank.required",
                        item.requiredRankName()).getString();
            }
            graphics.drawString(this.font, ellipsize(cost, Math.max(20, depositX - contentLeft - 32)),
                    contentLeft + 26, y + 15, locked ? 0xFFD8B15F : 0xFF9AA0A6, false);

            // Кнопка «Получить»
            boolean withdrawEnabled = snapshot.canWithdraw() && item.affordable()
                    && item.roleAllowed() && item.rankAllowed();
            boolean withdrawHovered = withdraw.contains(mouseX, mouseY);
            int withdrawColor = !withdrawEnabled ? 0xFF33333A : withdrawHovered ? 0xFF3E7A46 : 0xFF2E5A34;
            graphics.fill(withdrawX, buttonY, withdrawX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, withdrawColor);
            Component withdrawText = !item.roleAllowed()
                    ? Component.translatable("gui.pjmbasemod.role.locked_short")
                    : !item.rankAllowed()
                    ? Component.translatable("gui.pjmbasemod.rank.locked_short")
                    : item.affordable()
                    ? Component.translatable("gui.pjmbasemod.warehouse.withdraw")
                    : Component.translatable("gui.pjmbasemod.warehouse.no_points_short");
            int withdrawTextW = this.font.width(withdrawText);
            int wTotalW = 14 + 4 + withdrawTextW;
            int wStartX = withdrawX + (BUTTON_WIDTH - wTotalW) / 2;
            
            drawSmoothIcon(graphics, LOAD_ICON, wStartX, buttonY + 2, 14, 14);
            
            graphics.drawString(this.font, withdrawText, wStartX + 18, buttonY + 5,
                    withdrawEnabled ? 0xFFFFFFFF : 0xFF777777, false);

            // Кнопка «Сдать» (если предмет принимается складом)
            if (item.depositable()) {
                boolean depositEnabled = item.inventoryCount() > 0;
                boolean depositHovered = deposit.contains(mouseX, mouseY);
                int depositColor = !depositEnabled ? 0xFF33333A : depositHovered ? 0xFF456694 : 0xFF35506E;
                graphics.fill(depositX, buttonY, depositX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, depositColor);
                Component depositText = depositEnabled
                        ? Component.translatable("gui.pjmbasemod.warehouse.deposit_count", item.inventoryCount())
                        : Component.translatable("gui.pjmbasemod.warehouse.deposit");
                int depositTextW = this.font.width(depositText);
                int dTotalW = 14 + 4 + depositTextW;
                int dStartX = depositX + (BUTTON_WIDTH - dTotalW) / 2;
                
                drawSmoothIcon(graphics, UNLOAD_ICON, dStartX, buttonY + 2, 14, 14);
                
                graphics.drawString(this.font, depositText, dStartX + 18, buttonY + 5,
                        depositEnabled ? 0xFFFFFFFF : 0xFF777777, false);
            }

            y += ROW_HEIGHT;
        }

        if (items.isEmpty()) {
            Component empty = Component.translatable("gui.pjmbasemod.warehouse.empty");
            graphics.drawString(this.font, empty, contentLeft + 4, listTop() + 8, 0xFF888888, false);
        }
    }

    private String roleNames(List<String> roleIds) {
        List<String> names = new ArrayList<>();
        for (String roleId : roleIds) {
            names.add(Component.translatable("role.pjmbasemod." + roleId).getString());
        }
        return String.join(", ", names);
    }

    private String ellipsize(String text, int maxWidth) {
        return PjmGuiUtils.ellipsize(this.font, text, maxWidth);
    }
}
