package ru.liko.pjmbasemod.client.gui.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.gui.GuiItemIcons;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;
import ru.liko.pjmbasemod.common.garage.GarageSnapshot;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.CraftVehiclePacket;
import ru.liko.pjmbasemod.common.network.packet.RecycleVehiclePacket;
import ru.liko.pjmbasemod.common.network.packet.SpawnVehiclePacket;
import ru.liko.pjmbasemod.common.network.packet.StoreVehiclePacket;

import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

/**
 * GUI виртуального гаража: вкладка «Сборка» (каталог техники со стоимостью) и
 * вкладка «Гараж» (сохранённые экземпляры для спавна).
 */
public class GarageScreen extends Screen {

    private static final int GUI_WIDTH = 480;
    private static final int GUI_HEIGHT = 280;
    private static final int SIDEBAR_WIDTH = 120;
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 44;
    private static final int ROW_SPAWN_WIDTH = 86;
    private static final int ROW_RECYCLE_WIDTH = 92;
    private static final int ROW_BUTTON_GAP = 6;
    private static final int ROW_ACTION_HEIGHT = 20;
    private static final ResourceLocation LOCK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Pjmbasemod.MODID, "textures/icon/lock.png");

    private GarageSnapshot snapshot;
    private int tab; // 0 — сборка, 1 — гараж
    private int scroll;
    @Nullable
    private String cachedPreviewKey;
    @Nullable
    private Entity cachedPreviewEntity;

    private record PreviewTarget(String key, String displayName, String entityType, CompoundTag entityNbt) {}

    public GarageScreen(GarageSnapshot snapshot) {
        super(Component.translatable("gui.pjmbasemod.garage.title"));
        this.snapshot = snapshot;
    }

    /** Открыть экран (вызывается из обработчика пакета). */
    public static void open(GarageSnapshot snapshot) {
        Minecraft.getInstance().setScreen(new GarageScreen(snapshot));
    }

    /** Обновить данные, если экран сейчас открыт. */
    public void updateSnapshot(GarageSnapshot snapshot) {
        this.snapshot = snapshot;
        clampScroll();
    }

    public boolean isShowing() {
        return Minecraft.getInstance().screen == this;
    }

    @Override
    protected void init() {
        super.init();
        clampScroll();
    }

    private int guiLeft() { return (width - GUI_WIDTH) / 2; }
    private int guiTop() { return (height - GUI_HEIGHT) / 2; }

    private int rowsVisible() {
        int listHeight = GUI_HEIGHT - HEADER_HEIGHT - 20;
        return Math.max(1, listHeight / ROW_HEIGHT);
    }

    private int rowCount() {
        return tab == 0 ? snapshot.definitions().size() : snapshot.instances().size();
    }

    private void clampScroll() {
        int max = Math.max(0, rowCount() - rowsVisible());
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    private void switchTab(int newTab) {
        if (tab != newTab) {
            tab = newTab;
            scroll = 0;
        }
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
        if (mouseX >= left + GUI_WIDTH - 30 && mouseX <= left + GUI_WIDTH && mouseY >= top && mouseY <= top + HEADER_HEIGHT) {
            PjmUiSounds.playClick();
            this.onClose();
            return true;
        }

        // Вкладки
        if (mouseX >= left && mouseX <= left + SIDEBAR_WIDTH - 1) {
            if (mouseY >= top + HEADER_HEIGHT + 10 && mouseY <= top + HEADER_HEIGHT + 34) {
                PjmUiSounds.playClick();
                switchTab(0);
                return true;
            }
            if (mouseY >= top + HEADER_HEIGHT + 40 && mouseY <= top + HEADER_HEIGHT + 64) {
                PjmUiSounds.playClick();
                switchTab(1);
                return true;
            }
        }

        // Кнопка Store (Спрятать в гараж)
        if (tab == 1) {
            int storeX = left + 5;
            int storeY = top + GUI_HEIGHT - 30;
            int storeW = SIDEBAR_WIDTH - 10;
            int storeH = 24;
            if (mouseX >= storeX && mouseX <= storeX + storeW && mouseY >= storeY && mouseY <= storeY + storeH) {
                PjmUiSounds.playClick();
                if (snapshot.canStore()) {
                    PjmNetworking.sendToServer(StoreVehiclePacket.INSTANCE);
                }
                return true;
            }
        }

        // Клик по списку
        int contentLeft = left + SIDEBAR_WIDTH + 10;
        int contentTop = top + HEADER_HEIGHT + 10;
        int contentWidth = GUI_WIDTH - SIDEBAR_WIDTH - 20;
        int rows = rowsVisible();
        int y = contentTop;

        if (mouseX >= contentLeft && mouseX <= contentLeft + contentWidth) {
            if (tab == 0) {
                var defs = snapshot.definitions();
                for (int i = scroll; i < defs.size() && i < scroll + rows; i++) {
                    if (mouseY >= y && mouseY <= y + ROW_HEIGHT - 4) {
                        GarageSnapshot.DefEntry def = defs.get(i);
                        
                        int buttonWidth = 110;
                        int buttonX = contentLeft + contentWidth - buttonWidth - 8;
                        int buttonY = y + (ROW_HEIGHT - 4 - ROW_ACTION_HEIGHT) / 2;
                        boolean buttonHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth
                                && mouseY >= buttonY && mouseY <= buttonY + ROW_ACTION_HEIGHT;

                        if (snapshot.canCraft() && def.affordable() && def.roleAllowed() && buttonHovered) {
                            PjmUiSounds.playClick();
                            PjmNetworking.sendToServer(new CraftVehiclePacket(def.id()));
                        }
                        return true;
                    }
                    y += ROW_HEIGHT;
                }
            } else {
                var instances = snapshot.instances();
                for (int i = scroll; i < instances.size() && i < scroll + rows; i++) {
                    if (mouseY >= y && mouseY <= y + ROW_HEIGHT - 4) {
                        GarageSnapshot.InstanceEntry inst = instances.get(i);
                        int recycleX = contentLeft + contentWidth - ROW_RECYCLE_WIDTH - 8;
                        int spawnX = recycleX - ROW_BUTTON_GAP - ROW_SPAWN_WIDTH;
                        int buttonY = y + (ROW_HEIGHT - 4 - ROW_ACTION_HEIGHT) / 2;
                        boolean spawnHovered = mouseX >= spawnX && mouseX <= spawnX + ROW_SPAWN_WIDTH
                                && mouseY >= buttonY && mouseY <= buttonY + ROW_ACTION_HEIGHT;
                        boolean recycleHovered = mouseX >= recycleX && mouseX <= recycleX + ROW_RECYCLE_WIDTH
                                && mouseY >= buttonY && mouseY <= buttonY + ROW_ACTION_HEIGHT;
                        if (snapshot.canSpawn() && inst.roleAllowed() && spawnHovered) {
                            PjmUiSounds.playClick();
                            PjmNetworking.sendToServer(new SpawnVehiclePacket(inst.instanceId()));
                        } else if (snapshot.canStore() && inst.roleAllowed() && recycleHovered) {
                            PjmUiSounds.playClick();
                            PjmNetworking.sendToServer(new RecycleVehiclePacket(inst.instanceId()));
                        }
                        return true;
                    }
                    y += ROW_HEIGHT;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int left = guiLeft();
        int top = guiTop();

        // Основной фон окна
        graphics.fill(left, top, left + GUI_WIDTH, top + GUI_HEIGHT, 0xF216161A);
        
        // Обводка окна
        graphics.fill(left - 1, top - 1, left + GUI_WIDTH + 1, top, 0xFF353540);
        graphics.fill(left - 1, top + GUI_HEIGHT, left + GUI_WIDTH + 1, top + GUI_HEIGHT + 1, 0xFF353540);
        graphics.fill(left - 1, top, left, top + GUI_HEIGHT, 0xFF353540);
        graphics.fill(left + GUI_WIDTH, top, left + GUI_WIDTH + 1, top + GUI_HEIGHT, 0xFF353540);

        // Хедер
        graphics.fill(left, top, left + GUI_WIDTH, top + HEADER_HEIGHT, 0xFF1F1F26);

        // Сайдбар
        graphics.fill(left, top + HEADER_HEIGHT, left + SIDEBAR_WIDTH, top + GUI_HEIGHT, 0xFF1A1A20);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        
        int left = guiLeft();
        int top = guiTop();
        
        // Заголовок
        graphics.drawString(font, getTitle(), left + 8, top + 7, 0xFFE8E8E8, false);

        // Кнопка закрытия [✕]
        boolean hoverClose = mouseX >= left + GUI_WIDTH - 30 && mouseX <= left + GUI_WIDTH && mouseY >= top && mouseY <= top + HEADER_HEIGHT;
        graphics.drawString(font, "✕", left + GUI_WIDTH - 18, top + 7, hoverClose ? 0xFFD06060 : 0xFFB05050, false);

        // Вкладки
        drawTab(graphics, 0, Component.translatable("gui.pjmbasemod.garage.tab.craft").getString(), left, top + HEADER_HEIGHT + 10, mouseX, mouseY);
        drawTab(graphics, 1, Component.translatable("gui.pjmbasemod.garage.tab.garage").getString(), left, top + HEADER_HEIGHT + 40, mouseX, mouseY);

        // Кнопка Store (Спрятать в гараж)
        if (tab == 1) {
            boolean canStore = snapshot.canStore();
            int storeX = left + 5;
            int storeY = top + GUI_HEIGHT - 30;
            int storeW = SIDEBAR_WIDTH - 10;
            int storeH = 24;
            
            boolean hoverStore = mouseX >= storeX && mouseX <= storeX + storeW && mouseY >= storeY && mouseY <= storeY + storeH;
            int bgColor = canStore ? (hoverStore ? 0xFF35506E : 0xFF26262E) : 0xFF22222A;
            graphics.fill(storeX, storeY, storeX + storeW, storeY + storeH, bgColor);

            Component storeTextComp = Component.translatable("gui.pjmbasemod.garage.store_button");
            graphics.drawCenteredString(font, storeTextComp.getString(), storeX + storeW / 2, storeY + 8, canStore ? 0xFFFFFFFF : 0xFF777777);
        }

        drawSidebarPreview(graphics, hoveredPreview(mouseX, mouseY), left, top, partialTick);

        // Контент
        int contentLeft = left + SIDEBAR_WIDTH + 10;
        int contentTop = top + HEADER_HEIGHT + 10;
        int contentWidth = GUI_WIDTH - SIDEBAR_WIDTH - 20;
        int rows = rowsVisible();
        int y = contentTop;

        if (tab == 0) {
            var defs = snapshot.definitions();
            if (defs.isEmpty()) {
                graphics.drawCenteredString(font, Component.translatable("gui.pjmbasemod.garage.empty_catalog"), contentLeft + contentWidth / 2, contentTop + 50, 0xFF666666);
            } else {
                for (int i = scroll; i < defs.size() && i < scroll + rows; i++) {
                    GarageSnapshot.DefEntry def = defs.get(i);
                    boolean canCraft = snapshot.canCraft() && def.affordable() && def.roleAllowed();
                    drawRowCraft(graphics, def, canCraft, contentLeft, y, contentWidth, mouseX, mouseY);
                    y += ROW_HEIGHT;
                }
            }
        } else {
            var instances = snapshot.instances();
            if (instances.isEmpty()) {
                graphics.drawCenteredString(font, Component.translatable("gui.pjmbasemod.garage.empty_garage"), contentLeft + contentWidth / 2, contentTop + 50, 0xFF666666);
            } else {
                for (int i = scroll; i < instances.size() && i < scroll + rows; i++) {
                    GarageSnapshot.InstanceEntry inst = instances.get(i);
                    drawRowGarage(graphics, inst, snapshot.canSpawn() && inst.roleAllowed(),
                            snapshot.canStore() && inst.roleAllowed(), contentLeft, y, contentWidth, mouseX, mouseY);
                    y += ROW_HEIGHT;
                }
            }
        }

        // Скроллбар
        if (rowCount() > rowsVisible()) {
            int scrollX = left + GUI_WIDTH - 6;
            int scrollYStart = contentTop;
            int scrollHeight = rowsVisible() * ROW_HEIGHT - 4; // -4 чтобы выровнять с последней строкой
            
            graphics.fill(scrollX, scrollYStart, scrollX + 3, scrollYStart + scrollHeight, 0xFF1A1A20);

            int maxScroll = rowCount() - rowsVisible();
            int thumbHeight = Math.max(15, scrollHeight * rowsVisible() / rowCount());
            int thumbY = scrollYStart + (scrollHeight - thumbHeight) * scroll / maxScroll;
            graphics.fill(scrollX, thumbY, scrollX + 3, thumbY + thumbHeight, 0xFF35506E);
        }
    }

    private void drawTab(GuiGraphics graphics, int tabIndex, String text, int x, int y, int mouseX, int mouseY) {
        boolean isSelected = (this.tab == tabIndex);
        boolean isHovered = mouseX >= x + 6 && mouseX <= x + SIDEBAR_WIDTH - 6 && mouseY >= y && mouseY <= y + 24;

        int bg = isSelected ? 0xFF35506E : (isHovered ? 0xFF2E2E38 : 0xFF26262E);
        graphics.fill(x + 6, y, x + SIDEBAR_WIDTH - 6, y + 24, bg);

        int color = isSelected ? 0xFFFFFFFF : 0xFFB8B8B8;
        graphics.drawString(font, text, x + 12, y + 8, color, false);
    }

    private void drawRowCraft(GuiGraphics graphics, GarageSnapshot.DefEntry def, boolean canCraft, int x, int y, int width, int mouseX, int mouseY) {
        boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT - 4;

        boolean roleLocked = !def.roleAllowed();
        int bgColor = roleLocked ? 0xFF1D1D22 : (isHovered ? 0xFF2A2A33 : 0xFF222229);
        graphics.fill(x, y, x + width, y + ROW_HEIGHT - 4, bgColor);

        int buttonWidth = 110;
        int nameWidth = Math.max(20, width - buttonWidth - 80); // leave some space for time
        String name = ellipsize(def.displayName(), nameWidth);
        graphics.drawString(font, name, x + 12, y + 6, roleLocked ? 0xFF9A9A9A : 0xFFFFFFFF, true);
        
        if (def.assemblyTime() > 0) {
            int t = def.assemblyTime();
            String timeStr = t >= 3600 ? (t / 3600) + "ч " + ((t % 3600) / 60) + "м" : (t >= 60 ? (t / 60) + "м " + (t % 60) + "с" : t + "с");
            timeStr = timeStr.replace(" 0м", "").replace(" 0с", "");
            int nameLen = font.width(name);
            graphics.drawString(font, "⏱ " + timeStr, x + 12 + nameLen + 8, y + 6, 0xFFAAAAAA, false);
        }
        
        int costX = x + 12;
        int costY = y + 20;
        if (roleLocked) {
            graphics.blit(LOCK_TEXTURE, costX, costY - 1, 0, 0, 10, 10, 16, 16);
            String required = Component.translatable("gui.pjmbasemod.role.required",
                    roleNames(def.allowedRoles())).getString();
            graphics.drawString(font, ellipsize(required, Math.max(20, width - buttonWidth - 42)),
                    costX + 14, costY, 0xFFD8B15F, false);
        } else {
            // Отрисовка цены с настоящими иконками предметов.
            for (GarageSnapshot.CostView c : def.cost()) {
                ItemStack costIcon = GuiItemIcons.stackFor(c.item());
                graphics.pose().pushPose();
                graphics.pose().translate(costX, costY - 2, 100.0F);
                graphics.pose().scale(0.8F, 0.8F, 1.0F);
                graphics.renderItem(costIcon, 0, 0);
                graphics.pose().popPose();

                String countText = String.valueOf(c.count());
                int countColor = def.affordable() ? 0xFFDDDDDD : 0xFFFF5555;
                graphics.drawString(font, countText, costX + 14, costY, countColor, true);
                costX += 14 + font.width(countText) + 8;
            }
        }
        
        String status = roleLocked
                ? Component.translatable("gui.pjmbasemod.role.locked_short").getString().trim()
                : canCraft ? Component.translatable("gui.pjmbasemod.garage.craft_action").getString().trim() : Component.translatable("gui.pjmbasemod.garage.craft_disabled").getString().trim();
        if (status.equals("gui.pjmbasemod.garage.craft_action")) {
            status = "Собрать";
        } else if (status.equals("gui.pjmbasemod.garage.craft_disabled")) {
            status = "Нет ресурсов";
        }
        
        int buttonX = x + width - buttonWidth - 8;
        int buttonY = y + (ROW_HEIGHT - 4 - ROW_ACTION_HEIGHT) / 2;
        boolean buttonHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth
                && mouseY >= buttonY && mouseY <= buttonY + ROW_ACTION_HEIGHT;

        drawRowButton(graphics, buttonX, buttonY, buttonWidth, canCraft, buttonHovered,
                status, 0xFF2E5A34, 0xFF3E7A46);
    }

    private void drawRowGarage(GuiGraphics graphics, GarageSnapshot.InstanceEntry inst, boolean canSpawn, boolean canRecycle, int x, int y, int width, int mouseX, int mouseY) {
        boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT - 4;

        boolean roleLocked = !inst.roleAllowed();
        int bgColor = roleLocked ? 0xFF1D1D22 : (isHovered ? 0xFF2A2A33 : 0xFF222229);
        graphics.fill(x, y, x + width, y + ROW_HEIGHT - 4, bgColor);

        int recycleX = x + width - ROW_RECYCLE_WIDTH - 8;
        int spawnX = recycleX - ROW_BUTTON_GAP - ROW_SPAWN_WIDTH;
        int buttonY = y + (ROW_HEIGHT - 4 - ROW_ACTION_HEIGHT) / 2;
        int nameWidth = Math.max(20, spawnX - x - 20);
        graphics.drawString(font, ellipsize(inst.displayName(), nameWidth), x + 12, y + 8,
                roleLocked ? 0xFF9A9A9A : 0xFFFFFFFF, true);
        if (roleLocked) {
            graphics.blit(LOCK_TEXTURE, x + 12, y + 24, 0, 0, 10, 10, 16, 16);
            String required = Component.translatable("gui.pjmbasemod.role.required",
                    roleNames(inst.allowedRoles())).getString();
            graphics.drawString(font, ellipsize(required, Math.max(20, spawnX - x - 34)),
                    x + 26, y + 25, 0xFFD8B15F, false);
        }

        boolean spawnHovered = mouseX >= spawnX && mouseX <= spawnX + ROW_SPAWN_WIDTH
                && mouseY >= buttonY && mouseY <= buttonY + ROW_ACTION_HEIGHT;
        String spawnStatus = roleLocked
                ? Component.translatable("gui.pjmbasemod.role.locked_short").getString()
                : canSpawn
                ? Component.translatable("gui.pjmbasemod.garage.spawn_action").getString()
                : Component.translatable("gui.pjmbasemod.garage.spawn_disabled").getString();
        drawRowButton(graphics, spawnX, buttonY, ROW_SPAWN_WIDTH, canSpawn, spawnHovered,
                spawnStatus, 0xFF2E5A34, 0xFF3E7A46);

        boolean recycleHovered = mouseX >= recycleX && mouseX <= recycleX + ROW_RECYCLE_WIDTH
                && mouseY >= buttonY && mouseY <= buttonY + ROW_ACTION_HEIGHT;
        String recycleStatus = roleLocked
                ? Component.translatable("gui.pjmbasemod.role.locked_short").getString()
                : canRecycle
                ? Component.translatable("gui.pjmbasemod.garage.recycle_action").getString()
                : Component.translatable("gui.pjmbasemod.garage.recycle_disabled").getString();
        drawRowButton(graphics, recycleX, buttonY, ROW_RECYCLE_WIDTH, canRecycle, recycleHovered,
                recycleStatus, 0xFF5A342E, 0xFF7A463E);
    }

    private void drawRowButton(GuiGraphics graphics, int x, int y, int width, boolean enabled,
                               boolean hovered, String text, int normalColor, int hoverColor) {
        int buttonColor = enabled ? (hovered ? hoverColor : normalColor) : 0xFF33333A;
        graphics.fill(x, y, x + width, y + ROW_ACTION_HEIGHT, buttonColor);
        graphics.drawCenteredString(font, ellipsize(text, width - 8), x + width / 2, y + 6, enabled ? 0xFFFFFFFF : 0xFF777777);
    }

    private static String shortName(String itemId) {
        int idx = itemId.indexOf(':');
        return idx >= 0 ? itemId.substring(idx + 1) : itemId;
    }

    private String ellipsize(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        String result = text;
        while (!result.isEmpty() && font.width(result) + suffixWidth > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? suffix : result + suffix;
    }

    private String roleNames(java.util.List<String> roleIds) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String roleId : roleIds) {
            names.add(Component.translatable("role.pjmbasemod." + roleId).getString());
        }
        return String.join(", ", names);
    }

    @Nullable
    private PreviewTarget hoveredPreview(int mouseX, int mouseY) {
        int left = guiLeft();
        int top = guiTop();
        int contentLeft = left + SIDEBAR_WIDTH + 10;
        int contentTop = top + HEADER_HEIGHT + 10;
        int contentWidth = GUI_WIDTH - SIDEBAR_WIDTH - 20;

        if (mouseX < contentLeft || mouseX > contentLeft + contentWidth || mouseY < contentTop) {
            return null;
        }

        int relativeY = mouseY - contentTop;
        int visibleIndex = relativeY / ROW_HEIGHT;
        int rowY = contentTop + visibleIndex * ROW_HEIGHT;
        if (visibleIndex < 0 || visibleIndex >= rowsVisible() || mouseY > rowY + ROW_HEIGHT - 4) {
            return null;
        }

        int index = scroll + visibleIndex;
        if (tab == 0) {
            if (index >= snapshot.definitions().size()) return null;
            GarageSnapshot.DefEntry def = snapshot.definitions().get(index);
            if (def.entityType().isBlank()) return null;
            return new PreviewTarget("def:" + def.id(), def.displayName(), def.entityType(), new CompoundTag());
        }

        if (index >= snapshot.instances().size()) return null;
        GarageSnapshot.InstanceEntry inst = snapshot.instances().get(index);
        if (inst.entityType().isBlank()) return null;
        return new PreviewTarget("inst:" + inst.instanceId() + ":" + inst.entityNbt().hashCode(),
                inst.displayName(), inst.entityType(), inst.entityNbt());
    }

    private void drawSidebarPreview(GuiGraphics graphics, @Nullable PreviewTarget target, int left, int top, float partialTick) {
        if (target == null) {
            return;
        }

        int x = left + 8;
        int y = top + HEADER_HEIGHT + 76;
        int w = SIDEBAR_WIDTH - 16;
        int h = 118;

        graphics.fill(x, y, x + w, y + h, 0xFF202029);
        graphics.fill(x, y, x + w, y + 1, 0xFF353540);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF353540);
        graphics.fill(x, y, x + 1, y + h, 0xFF353540);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF353540);
        graphics.drawCenteredString(font, ellipsize(target.displayName(), w - 8), x + w / 2, y + 6, 0xFFFFFFFF);

        Entity entity = previewEntityFor(target);
        if (entity == null) {
            graphics.drawCenteredString(font, "Нет превью", x + w / 2, y + h / 2, 0xFFAAAAAA);
            return;
        }

        renderPreviewEntity(graphics, entity, x + 4, y + 18, x + w - 4, y + h - 4, partialTick);
    }

    @Nullable
    private Entity previewEntityFor(PreviewTarget target) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        if (target.key().equals(cachedPreviewKey) && cachedPreviewEntity != null) {
            return cachedPreviewEntity;
        }

        cachedPreviewKey = target.key();
        cachedPreviewEntity = null;

        ResourceLocation typeId = ResourceLocation.tryParse(target.entityType());
        if (typeId == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (type == null) {
            return null;
        }

        Entity entity = type.create(minecraft.level);
        if (entity == null) {
            return null;
        }

        if (!target.entityNbt().isEmpty()) {
            CompoundTag tag = target.entityNbt().copy();
            tag.remove("UUID");
            try {
                entity.load(tag);
            } catch (RuntimeException ignored) {
                // Если чужая техника не смогла прочитать NBT на клиенте, показываем дефолтную модель.
            }
        }
        entity.moveTo(0.0D, 0.0D, 0.0D, 35.0F, 0.0F);
        cachedPreviewEntity = entity;
        return entity;
    }

    private void renderPreviewEntity(GuiGraphics graphics, Entity entity, int x1, int y1, int x2, int y2, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        if (dispatcher.getRenderer(entity) == null) {
            graphics.drawCenteredString(font, "Нет рендера", (x1 + x2) / 2, (y1 + y2) / 2, 0xFFAAAAAA);
            return;
        }

        AABB box = entity.getBoundingBox();
        float entityWidth = (float) Math.max(box.getXsize(), box.getZsize());
        float entityHeight = Math.max(entity.getBbHeight(), (float) box.getYsize());
        float maxSize = Math.max(0.35F, Math.max(entityWidth, entityHeight));

        // Отдаляем модель
        float targetPixels = Math.min(x2 - x1, y2 - y1) * 0.38F;
        float scale = Mth.clamp(targetPixels / maxSize, 3.0F, 16.0F);

        float centerX = (x1 + x2) / 2.0F;
        // Фиксированный центр по вертикали — не зависит от размера хитбокса
        float centerY = (y1 + y2) / 2.0F;
        float spin = (Util.getMillis() % 10000L) / 10000.0F * 360.0F;

        graphics.enableScissor(x1, y1, x2, y2);
        graphics.pose().pushPose();
        boolean rendered = false;
        try {
            graphics.pose().translate(centerX, centerY, 90.0F);
            graphics.pose().scale(scale, scale, -scale);

            // Переворачиваем модель (стандартный флип для GUI-рендера)
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(180.0F));

            // Вид сверху: после ZP-флипа ось Y инвертирована, поэтому отрицательный угол = вид сверху
            graphics.pose().mulPose(Axis.XP.rotationDegrees(-25.0F));

            // Вращение модели вокруг оси Y
            graphics.pose().mulPose(Axis.YP.rotationDegrees(spin));

            Lighting.setupForEntityInInventory();
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0D, 0.0D, 0.0D,
                    0.0F, partialTick, graphics.pose(), graphics.bufferSource(), 15728880));
            graphics.flush();
            rendered = true;
        } catch (RuntimeException ignored) {
            // Не даём проблемному рендеру техники уронить весь экран гаража.
        } finally {
            dispatcher.setRenderShadow(true);
            graphics.pose().popPose();
            graphics.disableScissor();
            Lighting.setupFor3DItems();
        }

        if (!rendered) {
            graphics.drawCenteredString(font, "Нет превью", (x1 + x2) / 2, (y1 + y2) / 2, 0xFFAAAAAA);
        }
    }

    @Override
    public void removed() {
        cachedPreviewKey = null;
        cachedPreviewEntity = null;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
