package ru.liko.pjmbasemod.client.worldmap.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.Team;
import org.lwjgl.glfw.GLFW;
import ru.liko.pjmbasemod.client.basezone.ClientBaseZoneState;
import ru.liko.pjmbasemod.client.radiospawn.ClientRadioCarrierState;
import ru.liko.pjmbasemod.client.worldmap.WorldMapEngine;
import ru.liko.pjmbasemod.client.worldmap.data.MapConstants;
import ru.liko.pjmbasemod.client.worldmap.edit.CapturePointEditor;
import ru.liko.pjmbasemod.client.worldmap.edit.MapContextMenu;
import ru.liko.pjmbasemod.client.worldmap.overlay.MapOverlays;
import ru.liko.pjmbasemod.common.basezone.BaseZoneView;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.DeployToRadioPacket;
import ru.liko.pjmbasemod.common.network.packet.RadioSpawnListPacket;

/**
 * Полноэкранная карта в стиле JourneyMap/Xaero. Камера в мировых координатах, drag-пан,
 * плавный зум колесом к курсору, следование за игроком. Анимации: выезд снизу при открытии,
 * плавный зум, планер камеры к игроку (Пробел). НЕ наследует PjmBaseScreen — карта на весь экран.
 *
 * <p>Для OP — режим редактора точек захвата (тумблер слева сверху). ПКМ: десант к рации из базы
 * (любому), правка точек и телепорт (OP).
 */
public final class MapScreen extends Screen {

    private static final double MIN_SCALE = 0.0625;   // 1/16
    private static final double MAX_SCALE = 50.0;
    private static final double ZOOM_STEP = 1.2;
    private static final long SLIDE_MS = 280;
    private static final int TOGGLE_X = 6, TOGGLE_Y = 6, TOGGLE_W = 132, TOGGLE_H = 14;

    private double cameraX;
    private double cameraZ;
    private double scale = 3.0;       // текущий (анимируемый) px на блок
    private double destScale = 3.0;   // цель зума
    private boolean attached = true;  // камера жёстко следует за игроком
    private boolean gliding;          // планер камеры к игроку (Пробел)

    // Якорь зума к курсору (пока идёт анимация масштаба).
    private boolean zoomAnchor;
    private double zoomWorldX, zoomWorldZ, zoomScreenX, zoomScreenY;

    private boolean dragging;
    private double downMouseX, downMouseY, downCamX, downCamZ;

    private long openTime;

    private final MapContextMenu contextMenu = new MapContextMenu();

    public MapScreen() {
        super(Component.translatable("gui.pjmbasemod.map"));
    }

    @Override
    protected void init() {
        openTime = Util.getMillis();
        LocalPlayer p = this.minecraft != null ? this.minecraft.player : null;
        if (p != null) {
            cameraX = p.getX();
            cameraZ = p.getZ();
        }
        attached = true;
        gliding = false;
        scale = destScale;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        CapturePointEditor.get().commitAndDeselect();
        super.onClose();
    }

    // ─── доступы ───

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

    private float slideProgress() {
        float p = Mth.clamp((Util.getMillis() - openTime) / (float) SLIDE_MS, 0f, 1f);
        return 1f - (1f - p) * (1f - p) * (1f - p); // easeOutCubic
    }

    // ─── рендер ───

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partial) {
        Minecraft mc = this.minecraft;
        LocalPlayer player = mc != null ? mc.player : null;

        // Плавный зум к цели + удержание точки под курсором.
        if (Math.abs(scale - destScale) > 1e-4) {
            scale = Mth.lerp(0.35, scale, destScale);
            if (Math.abs(scale - destScale) < 1e-3) scale = destScale;
            if (zoomAnchor && !attached) {
                cameraX = zoomWorldX - (zoomScreenX - width / 2.0) / scale;
                cameraZ = zoomWorldZ - (zoomScreenY - height / 2.0) / scale;
            }
        } else {
            zoomAnchor = false;
        }

        // Планер к игроку / жёсткое следование.
        if (gliding && player != null) {
            cameraX = Mth.lerp(0.25, cameraX, player.getX());
            cameraZ = Mth.lerp(0.25, cameraZ, player.getZ());
            if (Math.abs(cameraX - player.getX()) < 0.5 && Math.abs(cameraZ - player.getZ()) < 0.5) {
                gliding = false;
                attached = true;
            }
        } else if (attached && player != null) {
            cameraX = player.getX();
            cameraZ = player.getZ();
        }

        gg.fill(0, 0, width, height, MapConstants.BACKGROUND_ARGB);

        // Анимация выезда снизу — весь контент карты едет вверх.
        float sp = slideProgress();
        boolean sliding = sp < 1f;
        if (sliding) {
            gg.pose().pushPose();
            gg.pose().translate(0, (1f - sp) * height, 0);
        }

        MapRenderer.render(gg, WorldMapEngine.get(), cameraX, cameraZ, scale, width, height);

        if (mc != null && mc.level != null) {
            String skip = editorOn() ? CapturePointEditor.get().selectedId() : null;
            MapOverlays.render(gg, font, cameraX, cameraZ, scale, width, height, dimStr(), skip);
            MapOverlays.drawRadioCarriers(gg, font, cameraX, cameraZ, scale, width, height);
        }
        if (editorOn()) {
            CapturePointEditor.get().render(gg, font, cameraX, cameraZ, scale, width, height);
        }

        drawPlayerArrow(gg, player);
        drawHud(gg, mouseX, mouseY);

        if (sliding) {
            gg.pose().popPose();
        }

        contextMenu.render(gg, font, mouseX, mouseY);
    }

    private void drawPlayerArrow(GuiGraphics gg, LocalPlayer player) {
        if (player == null) return;
        double px = MapRenderer.worldToScreenX(player.getX(), cameraX, scale, width);
        double py = MapRenderer.worldToScreenY(player.getZ(), cameraZ, scale, height);
        double yaw = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yaw), fy = Math.cos(yaw); // экранное направление взгляда
        double rx = -fy, ry = fx;
        double s = 7.0;
        double tipX = px + fx * s, tipY = py + fy * s;
        double blX = px - fx * s * 0.55 + rx * s * 0.7, blY = py - fy * s * 0.55 + ry * s * 0.7;
        double brX = px - fx * s * 0.55 - rx * s * 0.7, brY = py - fy * s * 0.55 - ry * s * 0.7;
        double[] xs = {tipX, blX, brX};
        double[] ys = {tipY, blY, brY};
        MapRenderer.fillPolygon(gg, xs, ys, 3, 0xFF33CCFF, width, height);
        MapRenderer.line(gg, tipX, tipY, blX, blY, 1.5f, 0xFF06283A);
        MapRenderer.line(gg, blX, blY, brX, brY, 1.5f, 0xFF06283A);
        MapRenderer.line(gg, brX, brY, tipX, tipY, 1.5f, 0xFF06283A);
    }

    private void drawHud(GuiGraphics gg, int mouseX, int mouseY) {
        int wx = Mth.floor(MapRenderer.screenToWorldX(mouseX, cameraX, scale, width));
        int wz = Mth.floor(MapRenderer.screenToWorldZ(mouseY, cameraZ, scale, height));
        drawPill(gg, width / 2, 6, "X " + wx + "    Z " + wz, 0xFFFFFFFF);
        drawPill(gg, width / 2, height - 20, String.format(Locale.ROOT, "%.2fx", scale), 0xFFB9C4D0);

        if (isOp()) {
            boolean on = CapturePointEditor.get().enabled();
            gg.fill(TOGGLE_X, TOGGLE_Y, TOGGLE_X + TOGGLE_W, TOGGLE_Y + TOGGLE_H, on ? 0xCC1E4D22 : 0xCC0A0A12);
            gg.fill(TOGGLE_X, TOGGLE_Y, TOGGLE_X + TOGGLE_W, TOGGLE_Y + 1, 0x30FFFFFF);
            gg.drawString(font, "✎ Правка точек: " + (on ? "ВКЛ" : "выкл"),
                    TOGGLE_X + 6, TOGGLE_Y + 3, on ? 0xFF9BE59B : 0xFFC0C0C0);
        }
    }

    private void drawPill(GuiGraphics gg, int cx, int topY, String text, int textColor) {
        int w = font.width(text);
        int x0 = cx - w / 2 - 6;
        int x1 = cx + w / 2 + 6;
        gg.fill(x0, topY, x1, topY + 14, 0xCC0A0A12);
        gg.fill(x0, topY, x1, topY + 1, 0x30FFFFFF);
        gg.fill(x0, topY + 13, x1, topY + 14, 0x40000000);
        gg.drawCenteredString(font, text, cx, topY + 3, textColor);
    }

    // ─── ввод ───

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        contextMenu.close();
        double pre = destScale;
        destScale = Mth.clamp(destScale * Math.pow(ZOOM_STEP, scrollY), MIN_SCALE, MAX_SCALE);
        if (!attached && destScale != pre) {
            zoomWorldX = (mouseX - width / 2.0) / scale + cameraX;
            zoomWorldZ = (mouseY - height / 2.0) / scale + cameraZ;
            zoomScreenX = mouseX;
            zoomScreenY = mouseY;
            zoomAnchor = true;
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
        if (button == 1) {
            BlockPos wb = worldBlockAt(mouseX, mouseY);
            double maxDist = Math.max(2.0, 10.0 / scale);
            List<MapContextMenu.Entry> items = new ArrayList<>();
            RadioSpawnListPacket.Entry carrier = carrierAt(wb, maxDist);
            if (carrier != null && inOwnBaseZone()) {
                UUID cid = carrier.id();
                items.add(MapContextMenu.Entry.leaf("Десант к " + carrier.owner(), () -> deployTo(cid)));
            }
            if (editorOn()) items.addAll(CapturePointEditor.get().contextEntries(wb, dimStr()));
            if (isOp()) items.add(MapContextMenu.Entry.leaf("Телепорт сюда", () -> teleportTo(wb.getX(), wb.getZ())));
            if (!items.isEmpty()) {
                contextMenu.open((int) mouseX, (int) mouseY, items);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (editorOn() && button == 0) {
            CapturePointEditor.get().handleLeftClick(worldBlockAt(mouseX, mouseY), dimStr(), Math.max(2.0, 8.0 / scale));
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
                return true;
            }
        }
        if (dragging && button == 0) {
            attached = false;
            gliding = false;
            ru.liko.pjmbasemod.client.gui.PjmCursor.applyGrab(); // курсор-«хватка» при панораме
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
            ru.liko.pjmbasemod.client.gui.PjmCursor.applyDefault();
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
            if (!attached) gliding = true; // плавно вернуть камеру к игроку
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ─── телепорт / десант ───

    private void teleportTo(int x, int z) {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.player == null || mc.getConnection() == null) return;
        int y = WorldMapEngine.get().heightAt(x, z);
        if (y == MapConstants.HEIGHT_UNSET) y = mc.player.blockPosition().getY();
        else y += 1;
        mc.getConnection().sendCommand("tp @s " + x + " " + y + " " + z);
        mc.setScreen(null);
    }

    private RadioSpawnListPacket.Entry carrierAt(BlockPos wb, double maxDist) {
        double bestSq = maxDist * maxDist;
        RadioSpawnListPacket.Entry best = null;
        for (RadioSpawnListPacket.Entry e : ClientRadioCarrierState.carriers()) {
            double dx = e.pos().getX() - wb.getX();
            double dz = e.pos().getZ() - wb.getZ();
            double d = dx * dx + dz * dz;
            if (d <= bestSq) {
                bestSq = d;
                best = e;
            }
        }
        return best;
    }

    private boolean inOwnBaseZone() {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.player == null) return false;
        Team t = mc.player.getTeam();
        if (t == null) return false;
        String myTeam = t.getName();
        String dim = dimStr();
        BlockPos pp = mc.player.blockPosition();
        for (BaseZoneView z : ClientBaseZoneState.zones()) {
            if (!z.dimension().equals(dim) || !z.owner().equalsIgnoreCase(myTeam)) continue;
            if (pp.getX() >= z.minX() && pp.getX() <= z.maxX()
                    && pp.getZ() >= z.minZ() && pp.getZ() <= z.maxZ()) {
                return true;
            }
        }
        return false;
    }

    private void deployTo(UUID carrierId) {
        PjmNetworking.sendToServer(new DeployToRadioPacket(carrierId));
        if (minecraft != null) minecraft.setScreen(null);
    }
}
