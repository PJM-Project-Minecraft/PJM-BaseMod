package ru.liko.pjmbasemod.client.worldmap.gui;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import ru.liko.pjmbasemod.client.worldmap.WorldMapEngine;
import ru.liko.pjmbasemod.client.worldmap.data.MapConstants;
import ru.liko.pjmbasemod.client.worldmap.edit.CapturePointEditor;
import ru.liko.pjmbasemod.client.worldmap.edit.MapContextMenu;
import ru.liko.pjmbasemod.client.worldmap.overlay.MapOverlays;

/**
 * Полноэкранная карта в стиле JourneyMap/Xaero. Камера в мировых координатах, drag-пан,
 * зум колесом к курсору, следование за игроком (пока не потащили). НЕ наследует PjmBaseScreen —
 * карта заполняет весь экран, а не масштабируемую панель.
 *
 * <p>Для OP — режим редактора точек захвата (тумблер слева сверху): ЛКМ выбор/вершина,
 * перетаскивание, ПКМ — контекст-меню. Логика в {@link CapturePointEditor}.
 */
public final class MapScreen extends Screen {

    private static final double MIN_SCALE = 0.0625;   // 1/16
    private static final double MAX_SCALE = 50.0;
    private static final double ZOOM_STEP = 1.2;

    private static final int TOGGLE_X = 4, TOGGLE_Y = 4, TOGGLE_W = 118, TOGGLE_H = 16;

    private double cameraX;
    private double cameraZ;
    private double scale = 3.0;      // px на блок; destScale Xaero по умолчанию = 3.0
    private boolean attached = true; // камера следует за игроком, пока не потащили

    private boolean dragging;
    private double downMouseX, downMouseY, downCamX, downCamZ;

    private final MapContextMenu contextMenu = new MapContextMenu();

    public MapScreen() {
        super(Component.translatable("gui.pjmbasemod.map"));
    }

    @Override
    protected void init() {
        LocalPlayer p = this.minecraft != null ? this.minecraft.player : null;
        if (p != null) {
            cameraX = p.getX();
            cameraZ = p.getZ();
        }
        attached = true;
    }

    @Override
    public boolean isPauseScreen() {
        return false; // мир (и скан карты) продолжают тикать
    }

    @Override
    public void onClose() {
        CapturePointEditor.get().commitAndDeselect();
        super.onClose();
    }

    private boolean isOp() {
        return minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    private boolean editorOn() {
        return isOp() && CapturePointEditor.get().enabled();
    }

    private String dimStr() {
        return (minecraft != null && minecraft.level != null)
                ? minecraft.level.dimension().location().toString() : "";
    }

    private BlockPos worldBlockAt(double mx, double my) {
        return new BlockPos(
                Mth.floor(MapRenderer.screenToWorldX(mx, cameraX, scale, width)), 0,
                Mth.floor(MapRenderer.screenToWorldZ(my, cameraZ, scale, height)));
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partial) {
        Minecraft mc = this.minecraft;
        LocalPlayer player = mc != null ? mc.player : null;
        if (attached && player != null) {
            cameraX = player.getX();
            cameraZ = player.getZ();
        }

        gg.fill(0, 0, width, height, MapConstants.BACKGROUND_ARGB);
        MapRenderer.render(gg, WorldMapEngine.get(), cameraX, cameraZ, scale, width, height);

        // Тактические оверлеи. Редактируемую точку рисует сам редактор — пропускаем её здесь.
        if (mc != null && mc.level != null) {
            String skip = editorOn() ? CapturePointEditor.get().selectedId() : null;
            MapOverlays.render(gg, font, cameraX, cameraZ, scale, width, height, dimStr(), skip);
        }
        if (editorOn()) {
            CapturePointEditor.get().render(gg, font, cameraX, cameraZ, scale, width, height);
        }

        // Маркер игрока.
        if (player != null) {
            int px = (int) MapRenderer.worldToScreenX(player.getX(), cameraX, scale, width);
            int pz = (int) MapRenderer.worldToScreenY(player.getZ(), cameraZ, scale, height);
            gg.fill(px - 3, pz - 3, px + 3, pz + 3, 0xFF000000);
            gg.fill(px - 2, pz - 2, px + 2, pz + 2, 0xFF33CCFF);
        }

        // Читаутки: координаты под курсором сверху, зум снизу.
        int wx = Mth.floor(MapRenderer.screenToWorldX(mouseX, cameraX, scale, width));
        int wz = Mth.floor(MapRenderer.screenToWorldZ(mouseY, cameraZ, scale, height));
        gg.drawCenteredString(font, "X " + wx + "   Z " + wz, width / 2, 4, 0xFFFFFFFF);
        gg.drawCenteredString(font, String.format(Locale.ROOT, "%.2fx", scale), width / 2, height - 14, 0xFFB0B0B0);

        // Тумблер редактора (только OP).
        if (isOp()) {
            boolean on = CapturePointEditor.get().enabled();
            gg.fill(TOGGLE_X, TOGGLE_Y, TOGGLE_X + TOGGLE_W, TOGGLE_Y + TOGGLE_H, on ? 0xC0295A2E : 0xB0000000);
            gg.drawString(font, "✎ Правка точек: " + (on ? "ВКЛ" : "выкл"),
                    TOGGLE_X + 5, TOGGLE_Y + 4, on ? 0xFF9BE59B : 0xFFB0B0B0);
        }

        contextMenu.render(gg, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        contextMenu.close();
        double pre = scale;
        scale = Mth.clamp(scale * Math.pow(ZOOM_STEP, scrollY), MIN_SCALE, MAX_SCALE);
        if (!attached && scale != pre) {
            // Держим мировую точку под курсором на месте (зум к курсору).
            double worldX = (mouseX - width / 2.0) / pre + cameraX;
            double worldZ = (mouseY - height / 2.0) / pre + cameraZ;
            cameraX = worldX - (mouseX - width / 2.0) / scale;
            cameraZ = worldZ - (mouseY - height / 2.0) / scale;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenu.visible()) {
            contextMenu.mouseClicked(mouseX, mouseY, font);
            return true;
        }
        if (isOp() && mouseX >= TOGGLE_X && mouseX <= TOGGLE_X + TOGGLE_W
                && mouseY >= TOGGLE_Y && mouseY <= TOGGLE_Y + TOGGLE_H) {
            CapturePointEditor.get().toggleEnabled();
            return true;
        }
        if (editorOn()) {
            BlockPos wb = worldBlockAt(mouseX, mouseY);
            double maxDist = Math.max(2.0, 8.0 / scale);
            if (button == 1) {
                contextMenu.open((int) mouseX, (int) mouseY,
                        CapturePointEditor.get().contextEntries(wb, dimStr()));
                return true;
            }
            if (button == 0) {
                CapturePointEditor.get().handleLeftClick(wb, dimStr(), maxDist);
            }
        }
        if (button == 0) {
            dragging = true;
            downMouseX = mouseX;
            downMouseY = mouseY;
            downCamX = cameraX;
            downCamZ = cameraZ;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (editorOn() && button == 0) {
            BlockPos wb = worldBlockAt(mouseX, mouseY);
            if (CapturePointEditor.get().handleDragTo(wb, dimStr(), Screen.hasControlDown())) {
                return true; // двигаем вершину/точку — не панорамируем
            }
        }
        if (dragging && button == 0) {
            attached = false;
            cameraX = downCamX + (downMouseX - mouseX) / scale;
            cameraZ = downCamZ + (downMouseY - mouseY) / scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
            CapturePointEditor.get().endDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && contextMenu.visible()) {
            contextMenu.close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            attached = true; // вернуть камеру к игроку
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
