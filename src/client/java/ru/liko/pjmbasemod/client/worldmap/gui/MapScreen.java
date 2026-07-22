package ru.liko.pjmbasemod.client.worldmap.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.mojang.math.Axis;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.Team;
import org.lwjgl.glfw.GLFW;
import ru.liko.pjmbasemod.client.basezone.ClientBaseZoneState;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.mapmarker.ClientMapMarkerState;
import ru.liko.pjmbasemod.client.radiospawn.ClientRadioCarrierState;
import ru.liko.pjmbasemod.client.worldmap.WorldMapEngine;
import ru.liko.pjmbasemod.client.worldmap.data.MapConstants;
import ru.liko.pjmbasemod.client.worldmap.edit.CapturePointEditor;
import ru.liko.pjmbasemod.client.worldmap.edit.MapContextMenu;
import ru.liko.pjmbasemod.client.worldmap.overlay.MapOverlays;
import ru.liko.pjmbasemod.common.basezone.BaseZoneView;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.DeployToRadioPacket;
import ru.liko.pjmbasemod.common.network.packet.MapMarkerActionPacket;
import ru.liko.pjmbasemod.common.network.packet.MapMarkerSyncPacket;
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
    private boolean closing;
    private long closeTime;

    private final MapContextMenu contextMenu = new MapContextMenu();

    /** Якорь стрелки (Squad-style): не null — идёт установка, стрелка тянется за мышью. */
    private BlockPos arrowStart;

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
        PjmNetworking.sendToServer(MapMarkerActionPacket.request()); // актуальные метки команды
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        CapturePointEditor.get().commitAndDeselect();
        startClosing(); // не закрываем сразу — проигрываем выезд вниз
    }

    private void startClosing() {
        if (closing) return;
        closing = true;
        closeTime = Util.getMillis();
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

    /** Видимость панели 1→в кадре, 0→за нижним краем. Открытие: 0→1 (easeOut). Закрытие: 1→0 (easeIn). */
    private float panelVisible() {
        if (closing) {
            float p = Mth.clamp((Util.getMillis() - closeTime) / (float) SLIDE_MS, 0f, 1f);
            return 1f - p * p * p; // easeInCubic → уезжает вниз
        }
        float p = Mth.clamp((Util.getMillis() - openTime) / (float) SLIDE_MS, 0f, 1f);
        return 1f - (1f - p) * (1f - p) * (1f - p); // easeOutCubic
    }

    // ─── рендер ───

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partial) {
        if (closing && Util.getMillis() - closeTime >= SLIDE_MS) {
            if (this.minecraft != null) this.minecraft.setScreen(null); // анимация выезда завершена
            return;
        }
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

        // Анимация выезда снизу (открытие/закрытие) — весь контент карты по вертикали.
        float sp = panelVisible();
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
            MapOverlays.drawTacticalMarkers(gg, font, cameraX, cameraZ, scale, width, height, dimStr());
        }
        if (arrowStart != null) { // превью стрелки: от якоря к курсору
            double ax = MapRenderer.worldToScreenX(arrowStart.getX() + 0.5, cameraX, scale, width);
            double ay = MapRenderer.worldToScreenY(arrowStart.getZ() + 0.5, cameraZ, scale, height);
            float pulse = 0.65f + 0.25f * (float) Math.sin(Util.getMillis() / 200.0);
            MapOverlays.drawArrowShape(gg, ax, ay, mouseX, mouseY, scale, PjmGuiUtils.ACCENT & 0xFFFFFF, pulse);
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

    private static final ResourceLocation PLAYER_ICON =
            ResourceLocation.fromNamespaceAndPath("pjmbasemod", "textures/gui/map/player.png");

    /** Маркер игрока — иконка player.png, повёрнутая остриём по направлению взгляда, бело-голубой тинт. */
    private void drawPlayerArrow(GuiGraphics gg, LocalPlayer player) {
        if (player == null) return;
        float px = (float) MapRenderer.worldToScreenX(player.getX(), cameraX, scale, width);
        float py = (float) MapRenderer.worldToScreenY(player.getZ(), cameraZ, scale, height);
        int iw = 11, ih = 17;
        gg.pose().pushPose();
        gg.pose().translate(px, py, 0);
        gg.pose().mulPose(Axis.ZP.rotationDegrees(player.getYRot())); // остриё по взгляду
        gg.setColor(0.80f, 0.92f, 1.0f, 1.0f);
        gg.blit(PLAYER_ICON, -iw / 2, -ih / 2, iw, ih, 0f, 0f, 196, 301, 196, 301);
        gg.setColor(1f, 1f, 1f, 1f);
        gg.pose().popPose();
    }

    private void drawHud(GuiGraphics gg, int mouseX, int mouseY) {
        int wx = Mth.floor(MapRenderer.screenToWorldX(mouseX, cameraX, scale, width));
        int wz = Mth.floor(MapRenderer.screenToWorldZ(mouseY, cameraZ, scale, height));
        drawPill(gg, width / 2, 6, "X " + wx + "    Z " + wz, PjmGuiUtils.TEXT_PRIMARY);
        drawPill(gg, width / 2, height - 20, String.format(Locale.ROOT, "%.2fx", scale), PjmGuiUtils.TEXT_DIM);
        if (arrowStart != null) {
            drawPill(gg, width / 2, height - 38, "ЛКМ — поставить стрелку   ·   ПКМ / Esc — отмена", PjmGuiUtils.ACCENT);
        }

        if (isOp()) {
            boolean on = CapturePointEditor.get().enabled();
            gg.fill(TOGGLE_X, TOGGLE_Y, TOGGLE_X + TOGGLE_W, TOGGLE_Y + TOGGLE_H,
                    on ? 0xCC4A3A16 : PjmGuiUtils.SCREEN_HEADER);
            gg.fill(TOGGLE_X, TOGGLE_Y, TOGGLE_X + TOGGLE_W, TOGGLE_Y + 1, PjmGuiUtils.ACCENT_DIM);
            gg.drawString(font, "✎ Правка точек: " + (on ? "ВКЛ" : "выкл"),
                    TOGGLE_X + 6, TOGGLE_Y + 3, on ? PjmGuiUtils.ACCENT : PjmGuiUtils.TEXT_MUTED);
        }
    }

    private void drawPill(GuiGraphics gg, int cx, int topY, String text, int textColor) {
        int w = font.width(text);
        int x0 = cx - w / 2 - 6;
        int x1 = cx + w / 2 + 6;
        gg.fill(x0, topY, x1, topY + 14, PjmGuiUtils.SCREEN_HEADER);
        gg.fill(x0, topY, x1, topY + 1, PjmGuiUtils.ACCENT_DIM);
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
        if (closing) return true; // во время анимации закрытия ввод игнорируем
        if (arrowStart != null) { // режим установки стрелки: ЛКМ — подтвердить, любая другая — отмена
            if (button == 0) {
                BlockPos end = worldBlockAt(mouseX, mouseY);
                PjmNetworking.sendToServer(MapMarkerActionPacket.placeArrow(
                        arrowStart.getX(), arrowStart.getZ(), end.getX(), end.getZ()));
            }
            arrowStart = null;
            return true;
        }
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
            MapMarkerSyncPacket.Entry marker = markerAt(wb, maxDist);
            if (marker != null && canRemoveMarker(marker)) {
                items.add(MapContextMenu.Entry.leaf("✕ Убрать метку", () -> PjmNetworking.sendToServer(
                        MapMarkerActionPacket.remove(marker.id()))));
            }
            if (minecraft != null && minecraft.player != null && minecraft.player.getTeam() != null) {
                items.add(MapContextMenu.Entry.sub("Поставить метку", List.of(
                        MapContextMenu.Entry.leaf("Стрелка (атака)", () -> arrowStart = wb),
                        MapContextMenu.Entry.leaf("Пехота противника", () -> placeMarker("infantry", wb)),
                        MapContextMenu.Entry.leaf("Техника противника", () -> placeMarker("vehicle", wb)),
                        MapContextMenu.Entry.leaf("Опасность", () -> placeMarker("danger", wb)))));
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && arrowStart != null) {
            arrowStart = null;
            return true;
        }
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
        startClosing();
    }

    // ─── тактические метки ───

    private void placeMarker(String type, BlockPos wb) {
        PjmNetworking.sendToServer(MapMarkerActionPacket.place(type, wb.getX(), wb.getZ()));
    }

    /** Убрать можно свою метку; командир фракции и OP — любую. Сервер проверяет авторитетно. */
    private boolean canRemoveMarker(MapMarkerSyncPacket.Entry marker) {
        if (isOp() || ru.liko.pjmbasemod.client.faction.ClientFactionCommanderState.state().active()) return true;
        return minecraft != null && minecraft.player != null
                && marker.owner().equals(minecraft.player.getGameProfile().getName());
    }

    private MapMarkerSyncPacket.Entry markerAt(BlockPos wb, double maxDist) {
        String dim = dimStr();
        double bestSq = maxDist * maxDist;
        MapMarkerSyncPacket.Entry best = null;
        for (MapMarkerSyncPacket.Entry m : ClientMapMarkerState.markers()) {
            if (!m.dimension().equals(dim)) continue;
            // у стрелки кликабельны оба конца
            double d = Math.min(distSq(m.x(), m.z(), wb), distSq(m.x2(), m.z2(), wb));
            if (d <= bestSq) {
                bestSq = d;
                best = m;
            }
        }
        return best;
    }

    private static double distSq(int x, int z, BlockPos wb) {
        double dx = x - wb.getX();
        double dz = z - wb.getZ();
        return dx * dx + dz * dz;
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
        startClosing();
    }
}
