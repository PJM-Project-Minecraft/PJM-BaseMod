package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.gui.GuiItemIcons;
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
public class WarehouseScreen extends Screen {

    private static final int GUI_WIDTH = 460;
    private static final int GUI_HEIGHT = 280;
    private static final int SIDEBAR_WIDTH = 120;
    private static final int HEADER_HEIGHT = 22;
    private static final int POOL_BAR_HEIGHT = 32;
    private static final int ROW_HEIGHT = 30;
    private static final int BUTTON_WIDTH = 76;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 4;
    private static final String EQUIPMENT_CATEGORY = "equipment";
    private static final ResourceLocation LOCK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Pjmbasemod.MODID, "textures/icon/lock.png");
    private static final ResourceLocation CLOSE_ICON = ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/close.png");
    private static final ResourceLocation POINTS_ICON = ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/points.png");
    private static final ResourceLocation LOAD_ICON = ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/load.png");
    private static final ResourceLocation UNLOAD_ICON = ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/unload.png");

    private void drawSmoothIcon(GuiGraphics graphics, ResourceLocation icon, int x, int y, int width, int height) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, icon);
        com.mojang.blaze3d.systems.RenderSystem.texParameter(3553, 10241, 9729); // GL_TEXTURE_MIN_FILTER, GL_LINEAR
        com.mojang.blaze3d.systems.RenderSystem.texParameter(3553, 10240, 9729); // GL_TEXTURE_MAG_FILTER, GL_LINEAR
        graphics.blit(icon, x, y, 0, 0, width, height, width, height);
    }

    private ResourceLocation getCategoryIcon(String category) {
        return switch (category) {
            case "weapon" -> ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/target.png");
            case "ammo" -> ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/target.png");
            case "armor" -> ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/personalization.png");
            case "medicine" -> ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/medicine.png");
            case "food" -> ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/food.png");
            case "equipment" -> ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/equipment.png");
            case "special" -> ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/class.png");
            case "raw" -> ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/menu.png");
            default -> null;
        };
    }

    private WarehouseSnapshot snapshot;
    private final List<String> categories = new ArrayList<>();
    private int selectedCategory;
    private int scroll;

    public WarehouseScreen(WarehouseSnapshot snapshot) {
        super(Component.translatable("gui.pjmbasemod.warehouse.title"));
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
        distinct.add(EQUIPMENT_CATEGORY);
        categories.clear();
        categories.addAll(distinct);
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

    private int guiLeft() { return (width - GUI_WIDTH) / 2; }
    private int guiTop() { return (height - GUI_HEIGHT) / 2; }

    private int listTop() { return guiTop() + HEADER_HEIGHT + POOL_BAR_HEIGHT + 6; }

    private int rowsVisible() {
        int listHeight = guiTop() + GUI_HEIGHT - 8 - listTop();
        return Math.max(1, listHeight / ROW_HEIGHT);
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
        if (scrollY != 0) {
            scroll -= (int) Math.signum(scrollY);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int left = guiLeft();
        int top = guiTop();

        // Закрыть
        if (mouseX >= left + GUI_WIDTH - 24 && mouseX <= left + GUI_WIDTH && mouseY >= top && mouseY <= top + HEADER_HEIGHT) {
            PjmUiSounds.playClick();
            this.onClose();
            return true;
        }

        // Вкладки категорий
        int catTop = top + HEADER_HEIGHT + 6;
        for (int i = 0; i < categories.size(); i++) {
            int y = catTop + i * 24;
            if (mouseX >= left + 6 && mouseX <= left + SIDEBAR_WIDTH - 6 && mouseY >= y && mouseY <= y + 20) {
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
        int contentLeft = left + SIDEBAR_WIDTH + 8;
        int contentWidth = GUI_WIDTH - SIDEBAR_WIDTH - 16;
        int rows = rowsVisible();
        List<WarehouseSnapshot.ItemEntry> items = currentItems();
        int y = listTop();
        for (int i = scroll; i < items.size() && i < scroll + rows; i++) {
            WarehouseSnapshot.ItemEntry item = items.get(i);
            int buttonY = y + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;

            int withdrawX = contentLeft + contentWidth - BUTTON_WIDTH - 4;
            int depositX = withdrawX - BUTTON_GAP - BUTTON_WIDTH;

            boolean withdrawHovered = mouseX >= withdrawX && mouseX <= withdrawX + BUTTON_WIDTH
                    && mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;
            if (withdrawHovered && snapshot.canWithdraw() && item.affordable()
                    && item.roleAllowed() && item.rankAllowed()) {
                int amount = hasShiftDown() ? item.maxPerWithdraw() : 1;
                PjmUiSounds.playClick();
                PjmNetworking.sendToServer(new WithdrawItemPacket(item.defId(), amount));
                return true;
            }

            boolean depositHovered = mouseX >= depositX && mouseX <= depositX + BUTTON_WIDTH
                    && mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;
            if (depositHovered && item.depositable() && item.inventoryCount() > 0) {
                int amount = hasShiftDown() ? Math.min(64, item.inventoryCount()) : 1;
                PjmUiSounds.playClick();
                PjmNetworking.sendToServer(new DepositItemPacket(item.defId(), amount));
                return true;
            }
            y += ROW_HEIGHT;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // super.render рисует фон + блюр; должен идти первым, иначе перекрывает наш GUI
        super.render(graphics, mouseX, mouseY, partialTick);

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

        // Сайдбар
        graphics.fill(left, top + HEADER_HEIGHT, left + SIDEBAR_WIDTH, top + GUI_HEIGHT, 0xFF1A1A20);
        int catTop = top + HEADER_HEIGHT + 6;
        for (int i = 0; i < categories.size(); i++) {
            int y = catTop + i * 24;
            boolean selected = i == selectedCategory;
            graphics.fill(left + 6, y, left + SIDEBAR_WIDTH - 6, y + 20, selected ? 0xFF35506E : 0xFF26262E);
            
            ResourceLocation icon = getCategoryIcon(categories.get(i));
            String label = Component.translatable("gui.pjmbasemod.warehouse.category." + categories.get(i)).getString();
            if (icon != null) {
                drawSmoothIcon(graphics, icon, left + 10, y + 3, 14, 14);
                graphics.drawString(this.font, label, left + 28, y + 6, selected ? 0xFFFFFFFF : 0xFFB8B8B8, false);
            } else {
                graphics.drawString(this.font, label, left + 12, y + 6, selected ? 0xFFFFFFFF : 0xFFB8B8B8, false);
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
            if (locked) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200.0F);
                graphics.fill(contentLeft + 4, itemY, contentLeft + 20, itemY + 16, 0xAA000000);
                graphics.pose().translate(contentLeft + 7, itemY + 3, 0);
                graphics.pose().scale(10.0F / 16.0F, 10.0F / 16.0F, 1.0F);
                graphics.blit(LOCK_TEXTURE, 0, 0, 0, 0, 16, 16, 16, 16);
                graphics.pose().popPose();
            }

            graphics.drawString(this.font, item.displayName(), contentLeft + 26, y + 4,
                    locked ? 0xFF9A9A9A : 0xFFE8E8E8, false);
            String cost = Component.translatable("gui.pjmbasemod.warehouse.cost", item.pointCost()).getString();
            if (item.depositable()) {
                cost += "  " + Component.translatable("gui.pjmbasemod.warehouse.refund", item.refundValue()).getString();
            }

            int buttonY = y + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;
            int withdrawX = contentLeft + contentWidth - BUTTON_WIDTH - 4;
            int depositX = withdrawX - BUTTON_GAP - BUTTON_WIDTH;
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
            boolean withdrawHovered = mouseX >= withdrawX && mouseX <= withdrawX + BUTTON_WIDTH
                    && mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;
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
                boolean depositHovered = mouseX >= depositX && mouseX <= depositX + BUTTON_WIDTH
                        && mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;
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
        if (this.font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int suffixWidth = this.font.width(suffix);
        String result = text;
        while (!result.isEmpty() && this.font.width(result) + suffixWidth > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? suffix : result + suffix;
    }
}
