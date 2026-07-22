package ru.liko.pjmbasemod.client.gui.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.Team;
import ru.liko.pjmbasemod.client.basezone.ClientBaseZoneState;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.worldmap.WorldMapEngine;
import ru.liko.pjmbasemod.client.worldmap.data.MapConstants;
import ru.liko.pjmbasemod.client.worldmap.gui.MapRenderer;
import ru.liko.pjmbasemod.client.worldmap.overlay.MapOverlays;
import ru.liko.pjmbasemod.common.basezone.BaseZoneView;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.MapMarkerActionPacket;
import ru.liko.pjmbasemod.common.network.packet.RadioSpawnListPacket;
import ru.liko.pjmbasemod.common.network.packet.RadioSpawnSelectPacket;

/**
 * Экран развёртывания после смерти (Squad-style): полноэкранная карта, на которой
 * кликом выбирается точка возрождения — база своей команды или радио-рюкзак.
 * Открывается автоматически из {@link PjmDeathScreen} после кинематики.
 * Закрыть нельзя (игрок мёртв) — только выбор точки или выход в главное меню.
 */
public final class DeploymentMapScreen extends Screen {

    private static final double MIN_SCALE = 0.0625, MAX_SCALE = 50.0, ZOOM_STEP = 1.2;
    private static final int HIT_RADIUS = 16; // px: клик-зона иконок спавна

    private final List<RadioSpawnListPacket.Entry> radios;
    private final long deathAt; // millis смерти — от него тикают перезарядки раций

    private double cameraX, cameraZ;
    private double scale = 1.5, destScale = 1.5;
    private boolean dragging;
    private double downMouseX, downMouseY, downCamX, downCamZ;
    private boolean deployed; // защита от двойного клика

    /** Точка спавна на карте: база или рация. */
    private record SpawnPoint(String label, int x, int z, RadioSpawnListPacket.Entry radio) {}

    public DeploymentMapScreen(List<RadioSpawnListPacket.Entry> radios, long deathAt) {
        super(Component.literal("Развёртывание"));
        this.radios = List.copyOf(radios);
        this.deathAt = deathAt;
    }

    @Override
    protected void init() {
        BaseZoneView base = ownBase();
        if (base != null) {
            cameraX = (base.minX() + base.maxX()) / 2.0;
            cameraZ = (base.minZ() + base.maxZ()) / 2.0;
        } else if (minecraft != null && minecraft.player != null) {
            cameraX = minecraft.player.getX();
            cameraZ = minecraft.player.getZ();
        }
        PjmNetworking.sendToServer(MapMarkerActionPacket.request()); // метки команды на карте
    }

    // ─── данные ───

    private String dimStr() {
        return (minecraft != null && minecraft.level != null)
                ? minecraft.level.dimension().location().toString() : "";
    }

    private String myTeam() {
        Team t = minecraft != null && minecraft.player != null ? minecraft.player.getTeam() : null;
        return t != null ? t.getName() : null;
    }

    private BaseZoneView ownBase() {
        String team = myTeam();
        if (team == null) return null;
        String dim = dimStr();
        for (BaseZoneView z : ClientBaseZoneState.zones()) {
            if (z.dimension().equals(dim) && z.owner().equalsIgnoreCase(team)) return z;
        }
        return null;
    }

    private int cooldownLeft(RadioSpawnListPacket.Entry radio) {
        if (radio.cooldownSeconds() <= 0) return 0;
        return Math.max(0, (int) Math.ceil(radio.cooldownSeconds() - (Util.getMillis() - deathAt) / 1000.0));
    }

    private List<SpawnPoint> spawnPoints() {
        List<SpawnPoint> points = new ArrayList<>();
        BaseZoneView base = ownBase();
        // «База» есть всегда — без зоны это обычный ванильный респавн.
        points.add(new SpawnPoint("БАЗА",
                base != null ? (base.minX() + base.maxX()) / 2 : 0,
                base != null ? (base.minZ() + base.maxZ()) / 2 : 0,
                null));
        for (RadioSpawnListPacket.Entry r : radios) {
            points.add(new SpawnPoint("Рация: " + r.owner(), r.pos().getX(), r.pos().getZ(), r));
        }
        return points;
    }

    // ─── рендер ───

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partial) {
        if (Math.abs(scale - destScale) > 1e-4) {
            scale = Mth.lerp(0.35, scale, destScale);
        }
        gg.fill(0, 0, width, height, MapConstants.BACKGROUND_ARGB);
        MapRenderer.render(gg, WorldMapEngine.get(), cameraX, cameraZ, scale, width, height);
        if (minecraft != null && minecraft.level != null) {
            MapOverlays.render(gg, font, cameraX, cameraZ, scale, width, height, dimStr(), null);
            MapOverlays.drawTacticalMarkers(gg, font, cameraX, cameraZ, scale, width, height, dimStr());
        }

        BaseZoneView base = ownBase();
        List<SpawnPoint> points = spawnPoints();
        SpawnPoint hovered = pointAtScreen(points, mouseX, mouseY, base != null);
        for (SpawnPoint p : points) {
            if (p.radio() == null && base == null) continue; // безымянная «база» — только в панели
            int sx = (int) MapRenderer.worldToScreenX(p.x() + 0.5, cameraX, scale, width);
            int sy = (int) MapRenderer.worldToScreenY(p.z() + 0.5, cameraZ, scale, height);
            if (sx < -40 || sx > width + 40 || sy < -40 || sy > height + 40) continue;
            if (p == hovered) { // подсветка наведения
                gg.fill(sx - 12, sy - 12, sx + 12, sy + 12, PjmGuiUtils.withAlpha(PjmGuiUtils.ACCENT, 0x30));
            }
            if (p.radio() != null) {
                MapOverlays.drawRadioMarker(gg, font, sx, sy, p.radio().owner(), cooldownLeft(p.radio()));
            } else {
                MapOverlays.drawRadioMarker(gg, font, sx, sy, "БАЗА", 0);
            }
        }

        drawHeader(gg);
        drawPanel(gg, mouseX, mouseY, points, base != null);
    }

    private void drawHeader(GuiGraphics gg) {
        String text = "ВЫБЕРИ ТОЧКУ ВОЗРОЖДЕНИЯ";
        int w = font.width(text);
        int x0 = width / 2 - w / 2 - 8, x1 = width / 2 + w / 2 + 8;
        gg.fill(x0, 6, x1, 22, PjmGuiUtils.SCREEN_HEADER);
        gg.fill(x0, 20, x1, 22, PjmGuiUtils.ACCENT_DIM);
        gg.drawCenteredString(font, text, width / 2, 10, PjmGuiUtils.ACCENT);
    }

    // ─── панель-список слева ───

    private static final int PANEL_X = 8, PANEL_Y = 8, PANEL_W = 190, ROW_H = 16, HEADER_H = 18;

    private void drawPanel(GuiGraphics gg, int mouseX, int mouseY, List<SpawnPoint> points, boolean hasBase) {
        int rows = points.size() + 1; // + «Главное меню»
        int h = HEADER_H + rows * ROW_H + 6;
        PjmGuiUtils.drawScreenPanel(gg, PANEL_X, PANEL_Y, PANEL_W, h, 0, HEADER_H);
        gg.drawString(font, "РАЗВЁРТЫВАНИЕ", PANEL_X + 6, PANEL_Y + 5, PjmGuiUtils.TEXT_LABEL);
        int row = 0;
        for (SpawnPoint p : points) {
            int ry = rowY(row);
            boolean hover = inRow(mouseX, mouseY, row);
            int cd = p.radio() != null ? cooldownLeft(p.radio()) : 0;
            if (hover && cd == 0) gg.fill(PANEL_X + 1, ry - 2, PANEL_X + PANEL_W - 1, ry + ROW_H - 4, PjmGuiUtils.SCREEN_ROW_HOVER);
            String label = p.radio() == null && !hasBase ? p.label() + " (точка возрождения)" : p.label();
            if (cd > 0) label += " — " + cd + "с";
            gg.drawString(font, PjmGuiUtils.ellipsize(font, label, PANEL_W - 12), PANEL_X + 6, ry,
                    cd > 0 ? PjmGuiUtils.TEXT_MUTED : (hover ? PjmGuiUtils.ACCENT : PjmGuiUtils.TEXT_PRIMARY));
            row++;
        }
        int ry = rowY(row);
        boolean hover = inRow(mouseX, mouseY, row);
        if (hover) gg.fill(PANEL_X + 1, ry - 2, PANEL_X + PANEL_W - 1, ry + ROW_H - 4, PjmGuiUtils.SCREEN_ROW_HOVER);
        gg.drawString(font, "⏻ Главное меню", PANEL_X + 6, ry, hover ? PjmGuiUtils.ACCENT : PjmGuiUtils.TEXT_DIM);
    }

    private int rowY(int row) {
        return PANEL_Y + HEADER_H + 4 + row * ROW_H;
    }

    private boolean inRow(double mx, double my, int row) {
        int ry = rowY(row);
        return mx >= PANEL_X && mx <= PANEL_X + PANEL_W && my >= ry - 2 && my < ry + ROW_H - 4;
    }

    // ─── выбор точки ───

    private SpawnPoint pointAtScreen(List<SpawnPoint> points, double mx, double my, boolean hasBase) {
        SpawnPoint best = null;
        double bestSq = (double) HIT_RADIUS * HIT_RADIUS;
        for (SpawnPoint p : points) {
            if (p.radio() == null && !hasBase) continue;
            double sx = MapRenderer.worldToScreenX(p.x() + 0.5, cameraX, scale, width);
            double sy = MapRenderer.worldToScreenY(p.z() + 0.5, cameraZ, scale, height);
            double d = (sx - mx) * (sx - mx) + (sy - my) * (sy - my);
            if (d <= bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }

    private void deploy(SpawnPoint p) {
        if (deployed || minecraft == null || minecraft.player == null) return;
        if (p.radio() != null) {
            if (cooldownLeft(p.radio()) > 0) return;
            PjmNetworking.sendToServer(new RadioSpawnSelectPacket(p.radio().id()));
        }
        deployed = true;
        minecraft.player.respawn();
    }

    // ─── ввод ───

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        List<SpawnPoint> points = spawnPoints();
        // панель
        for (int i = 0; i < points.size(); i++) {
            if (inRow(mouseX, mouseY, i)) {
                deploy(points.get(i));
                return true;
            }
        }
        if (inRow(mouseX, mouseY, points.size())) {
            TacticalPauseMenuScreen.disconnectToTitle(minecraft);
            return true;
        }
        // иконка на карте
        SpawnPoint hit = pointAtScreen(points, mouseX, mouseY, ownBase() != null);
        if (hit != null) {
            deploy(hit);
            return true;
        }
        dragging = true;
        downMouseX = mouseX;
        downMouseY = mouseY;
        downCamX = cameraX;
        downCamZ = cameraZ;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
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
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        destScale = Mth.clamp(destScale * Math.pow(ZOOM_STEP, scrollY), MIN_SCALE, MAX_SCALE);
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // игрок мёртв — уйти можно только выбором точки или в главное меню
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
