package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.common.capturepoint.CapturePoint;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.CapturePointEditorActionPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Редактор точек захвата (OP-only). Слева — список точек, справа — канвас-карта
 * с координатной сеткой и маркером игрока. Клик по канвасу добавляет вершину
 * к выбранной точке, перетаскивание — двигает ближайшую вершину.
 *
 * <p>Координаты канваса — блочные X/Z. Центр карты = позиция игрока (или 0,0).
 * Масштаб: 1 блок = {@code cellSize} пикселей (по умолчанию 2).</p>
 */
public class CapturePointEditorScreen extends PjmBaseScreen {

    private static final int GUI_WIDTH = 620;
    private static final int GUI_HEIGHT = 380;
    private static final int SIDEBAR_WIDTH = 160;
    private static final int HEADER_HEIGHT = 24;
    private static final int CANVAS_PADDING = 8;
    private static final int CANVAS_CELL = 4;        // пикселей на блок
    private static final int VERTEX_RADIUS = 5;       // радиус кружка вершины
    private static final int VERTEX_HIT_RADIUS = 8;   // радиус клика

    private final List<CapturePoint> serverPoints;
    private int selectedPoint = -1;
    @Nullable
    private EditBox nameBox;
    @Nullable
    private int draggingVertex = -1;
    private double camX = 0;
    private double camZ = 0;
    private boolean camInitialized;

    public CapturePointEditorScreen(List<CapturePoint> points) {
        super(Component.translatable("gui.pjmbasemod.capturepoint_editor.title"), GUI_WIDTH, GUI_HEIGHT);
        this.serverPoints = new ArrayList<>(points);
    }

    public static void open(List<CapturePoint> points) {
        Minecraft.getInstance().setScreen(new CapturePointEditorScreen(points));
    }

    @Override
    protected void init() {
        super.init();
        int left = guiLeft();
        int top = guiTop();
        nameBox = new EditBox(this.font, left + SIDEBAR_WIDTH + 12, top + HEADER_HEIGHT + 8,
                200, 16, Component.literal("name"));
        nameBox.setMaxLength(64);
        nameBox.setBordered(true);
        nameBox.setTextColor(PjmGuiUtils.TEXT_PRIMARY);
        if (selectedPoint >= 0 && selectedPoint < serverPoints.size()) {
            nameBox.setValue(serverPoints.get(selectedPoint).displayName());
        }
        addWidget(nameBox);

        if (!camInitialized) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                camX = mc.player.getX();
                camZ = mc.player.getZ();
            }
            camInitialized = true;
        }
    }

    @Override
    public void onClose() {
        saveCurrentName();
        super.onClose();
    }

    private void saveCurrentName() {
        if (selectedPoint >= 0 && selectedPoint < serverPoints.size() && nameBox != null) {
            CapturePoint cp = serverPoints.get(selectedPoint);
            String newName = nameBox.getValue();
            if (!newName.equals(cp.displayName())) {
                serverPoints.set(selectedPoint, withName(cp, newName));
                PjmNetworking.sendToServer(new CapturePointEditorActionPacket(
                        CapturePointEditorActionPacket.Action.UPDATE_DISPLAY_NAME,
                        cp.id(), newName, cp.dimension(), cp.ownerTeamId(), cp.vertices()));
            }
        }
    }

    private static CapturePoint withName(CapturePoint cp, String name) {
        return new CapturePoint(cp.id(), name, cp.dimension(), cp.vertices(),
                cp.ownerTeamId(), cp.captureTeamId(), cp.progressPercent(), cp.contested());
    }

    private static CapturePoint withVertices(CapturePoint cp, List<CapturePoint.Vertex> v) {
        return new CapturePoint(cp.id(), cp.displayName(), cp.dimension(), v,
                cp.ownerTeamId(), cp.captureTeamId(), cp.progressPercent(), cp.contested());
    }

    @Override
    protected void renderScaled(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = guiLeft();
        int top = guiTop();
        PjmGuiUtils.drawScreenPanel(g, left, top, GUI_WIDTH, GUI_HEIGHT, SIDEBAR_WIDTH, HEADER_HEIGHT);

        // Заголовок
        g.drawString(font, getTitle(), left + 8, top + 7, PjmGuiUtils.TEXT_PRIMARY, false);

        // Сайдбар — список точек
        renderSidebar(g, left, top, mouseX, mouseY);

        // Канвас
        int cx = left + SIDEBAR_WIDTH + CANVAS_PADDING;
        int cy = top + HEADER_HEIGHT + CANVAS_PADDING;
        int cw = GUI_WIDTH - SIDEBAR_WIDTH - CANVAS_PADDING * 2;
        int ch = GUI_HEIGHT - HEADER_HEIGHT - CANVAS_PADDING * 2;
        renderCanvas(g, cx, cy, cw, ch, mouseX, mouseY);

        // Поле имени + кнопки
        if (nameBox != null) nameBox.render(g, mouseX, mouseY, partialTick);
        renderButtons(g, left, top, mouseX, mouseY);
    }

    private void renderSidebar(GuiGraphics g, int left, int top, int mx, int my) {
        int x = left + 4;
        int y = top + HEADER_HEIGHT + 4;
        int w = SIDEBAR_WIDTH - 8;
        g.drawString(font, "Точки:", x, y, PjmGuiUtils.TEXT_LABEL, false);
        y += 14;
        int rowH = 18;
        for (int i = 0; i < serverPoints.size(); i++) {
            CapturePoint cp = serverPoints.get(i);
            boolean selected = i == selectedPoint;
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + rowH;
            int bg = selected ? PjmGuiUtils.SCREEN_SELECT : hover ? PjmGuiUtils.SCREEN_ROW_HOVER : PjmGuiUtils.SCREEN_ROW;
            g.fill(x, y, x + w, y + rowH - 1, bg);
            String label = cp.id();
            if (cp.vertices().size() >= 3) label += " ▶";
            g.drawString(font, label, x + 4, y + 5,
                    selected ? PjmGuiUtils.TEXT_PRIMARY : PjmGuiUtils.TEXT_DIM, false);
            y += rowH;
        }
        if (serverPoints.isEmpty()) {
            g.drawString(font, "(нет точек)", x + 4, y + 5, PjmGuiUtils.TEXT_MUTED, false);
        }
    }

    private void renderButtons(GuiGraphics g, int left, int top, int mx, int my) {
        int x = left + SIDEBAR_WIDTH + 220;
        int y = top + HEADER_HEIGHT + 8;
        int w = 70;
        int h = 16;
        // [+ Точка]
        drawButton(g, x, y, w, h, "+ Точка", mx, my, "add");
        // [Удалить]
        drawButton(g, x + w + 4, y, w, h, "Удалить", mx, my, "remove");
        // [Центр на мне]
        drawButton(g, x, y + 20, w, h, "Центр", mx, my, "center");
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my, String id) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        int bg = hover ? PjmGuiUtils.SCREEN_ROW_HOVER : PjmGuiUtils.SCREEN_ROW;
        g.fill(x, y, x + w, y + h, bg);
        PjmGuiUtils.drawBorder(g, x, y, w, h, PjmGuiUtils.SCREEN_BORDER);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, PjmGuiUtils.TEXT_PRIMARY);
    }

    private String buttonAt(int mx, int my, int left, int top) {
        int x = left + SIDEBAR_WIDTH + 220;
        int y = top + HEADER_HEIGHT + 8;
        int w = 70, h = 16;
        if (mx >= x && mx < x + w && my >= y && my < y + h) return "add";
        if (mx >= x + w + 4 && mx < x + w * 2 + 4 && my >= y && my < y + h) return "remove";
        if (mx >= x && mx < x + w && my >= y + 20 && my < y + 20 + h) return "center";
        return null;
    }

    private void renderCanvas(GuiGraphics g, int cx, int cy, int cw, int ch, int mx, int my) {
        // Фон канваса
        g.fill(cx, cy, cx + cw, cy + ch, 0xFF0E0E12);
        PjmGuiUtils.drawBorder(g, cx, cy, cw, ch, PjmGuiUtils.SCREEN_BORDER);

        int centerX = cx + cw / 2;
        int centerZ = cy + ch / 2;

        // Сетка (каждые 16 блоков = чанк)
        int gridColor = 0xFF1A1A22;
        int chunkPx = CANVAS_CELL * 16;
        for (int gx = centerX % chunkPx; gx < cw; gx += chunkPx) {
            g.fill(cx + gx, cy, cx + gx + 1, cy + ch, gridColor);
        }
        for (int gz = centerZ % chunkPx; gz < ch; gz += chunkPx) {
            g.fill(cx, cy + gz, cx + cw, cy + gz + 1, gridColor);
        }
        // Оси
        g.fill(centerX, cy, centerX + 1, cy + ch, 0xFF333344);
        g.fill(cx, centerZ, cx + cw, centerZ + 1, 0xFF333344);

        // Маркер игрока
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int px = centerX + (int) ((mc.player.getX() - camX) * CANVAS_CELL);
            int pz = centerZ + (int) ((mc.player.getZ() - camZ) * CANVAS_CELL);
            if (px >= cx && px < cx + cw && pz >= cy && pz < cy + ch) {
                g.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFF4A90E2);
            }
        }

        // Полигоны всех точек
        for (int i = 0; i < serverPoints.size(); i++) {
            CapturePoint cp = serverPoints.get(i);
            if (cp.vertices().size() < 2) continue;
            boolean selected = i == selectedPoint;
            int color = selected ? 0xFFE67E22 : 0xFF5566AA;
            drawPolygon(g, cp.vertices(), centerX, centerZ, color, selected);
        }

        // Подсказка
        if (selectedPoint < 0) {
            g.drawCenteredString(font, "Выбери точку слева или создай новую", centerX, cy + ch / 2 - 4, PjmGuiUtils.TEXT_MUTED);
        } else if (serverPoints.get(selectedPoint).vertices().size() < 3) {
            g.drawCenteredString(font, "Кликай по канвасу — добавляй вершины (≥3)", centerX, cy + ch - 14, PjmGuiUtils.TEXT_DIM);
        }
        // Координаты курсора в блоках
        if (mx >= cx && mx < cx + cw && my >= cy && my < cy + ch) {
            int bx = (int) Math.round((mx - centerX) / (double) CANVAS_CELL + camX);
            int bz = (int) Math.round((my - centerZ) / (double) CANVAS_CELL + camZ);
            g.drawString(font, "X:" + bx + " Z:" + bz, cx + 4, cy + ch - 12, PjmGuiUtils.TEXT_MUTED, false);
        }
    }

    private void drawPolygon(GuiGraphics g, List<CapturePoint.Vertex> verts, int centerX, int centerZ, int color, boolean filled) {
        int n = verts.size();
        int[] xs = new int[n];
        int[] zs = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = centerX + (int) ((verts.get(i).x() - camX) * CANVAS_CELL);
            zs[i] = centerZ + (int) ((verts.get(i).z() - camZ) * CANVAS_CELL);
        }
        // Заливка (треугольниками веером — упрощённо, только рамка для тонких полигонов)
        if (filled && n >= 3) {
            for (int i = 1; i < n - 1; i++) {
                fillTriangle(g, xs[0], zs[0], xs[i], zs[i], xs[i + 1], zs[i + 1], (color & 0x00FFFFFF) | 0x30000000);
            }
        }
        // Рёбра
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            drawLine(g, xs[i], zs[i], xs[j], zs[j], color);
        }
        // Вершины
        for (int i = 0; i < n; i++) {
            g.fill(xs[i] - VERTEX_RADIUS, zs[i] - VERTEX_RADIUS, xs[i] + VERTEX_RADIUS + 1, zs[i] + VERTEX_RADIUS + 1, color);
            g.fill(xs[i] - 2, zs[i] - 2, xs[i] + 3, zs[i] + 3, 0xFFFFFFFF);
        }
    }

    private static void drawLine(GuiGraphics g, int x0, int z0, int x1, int z1, int color) {
        // Bresenham-упрощённо через fill по шагам
        int dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0);
        int steps = Math.max(dx, dz);
        if (steps == 0) { g.fill(x0, z0, x0 + 1, z0 + 1, color); return; }
        for (int s = 0; s <= steps; s++) {
            int x = x0 + (x1 - x0) * s / steps;
            int z = z0 + (z1 - z0) * s / steps;
            g.fill(x, z, x + 1, z + 1, color);
        }
    }

    private static void fillTriangle(GuiGraphics g, int x0, int z0, int x1, int z1, int x2, int z2, int color) {
        // Bounding-box rasterization с барицентрической проверкой
        int minX = Math.min(x0, Math.min(x1, x2));
        int maxX = Math.max(x0, Math.max(x1, x2));
        int minZ = Math.min(z0, Math.min(z1, z2));
        int maxZ = Math.max(z0, Math.max(z1, z2));
        int area = (x1 - x0) * (z2 - z0) - (x2 - x0) * (z1 - z0);
        if (area == 0) return;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int w0 = (x1 - x) * (z2 - z) - (x2 - x) * (z1 - z);
                int w1 = (x2 - x) * (z0 - z) - (x0 - x) * (z2 - z);
                int w2 = (x0 - x) * (z1 - z) - (x1 - x) * (z0 - z);
                boolean inside = (area > 0 && w0 >= 0 && w1 >= 0 && w2 >= 0)
                        || (area < 0 && w0 <= 0 && w1 <= 0 && w2 <= 0);
                if (inside) g.fill(x, z, x + 1, z + 1, color);
            }
        }
    }

    @Override
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int left = guiLeft(), top = guiTop();
        String btn = buttonAt(mouseX, mouseY, left, top);
        if (btn != null) { handleButton(btn); return true; }

        int sx = left + 4, sy = top + HEADER_HEIGHT + 18, sw = SIDEBAR_WIDTH - 8;
        if (mouseX >= sx && mouseX < sx + sw) {
            int idx = (mouseY - sy) / 18;
            if (idx >= 0 && idx < serverPoints.size()) {
                saveCurrentName();
                selectedPoint = idx;
                if (nameBox != null) nameBox.setValue(serverPoints.get(idx).displayName());
                return true;
            }
        }

        int cx = left + SIDEBAR_WIDTH + CANVAS_PADDING, cy = top + HEADER_HEIGHT + CANVAS_PADDING;
        int cw = GUI_WIDTH - SIDEBAR_WIDTH - CANVAS_PADDING * 2, ch = GUI_HEIGHT - HEADER_HEIGHT - CANVAS_PADDING * 2;
        if (mouseX < cx || mouseX >= cx + cw || mouseY < cy || mouseY >= cy + ch) return false;
        if (selectedPoint < 0) return false;
        int centerX = cx + cw / 2, centerZ = cy + ch / 2;
        int hit = findVertexAt(mouseX, mouseY, centerX, centerZ);
        if (hit >= 0) { draggingVertex = hit; return true; }
        int bx = (int) Math.round((mouseX - centerX) / (double) CANVAS_CELL + camX);
        int bz = (int) Math.round((mouseY - centerZ) / (double) CANVAS_CELL + camZ);
        addVertex(bx, bz);
        return true;
    }

    @Override
    protected boolean mouseDraggedScaled(int mouseX, int mouseY, int button, double dragX, double dragY) {
        if (draggingVertex < 0 || selectedPoint < 0) return false;
        int left = guiLeft(), top = guiTop();
        int cx = left + SIDEBAR_WIDTH + CANVAS_PADDING, cy = top + HEADER_HEIGHT + CANVAS_PADDING;
        int cw = GUI_WIDTH - SIDEBAR_WIDTH - CANVAS_PADDING * 2, ch = GUI_HEIGHT - HEADER_HEIGHT - CANVAS_PADDING * 2;
        int bx = (int) Math.round((mouseX - (cx + cw / 2)) / (double) CANVAS_CELL + camX);
        int bz = (int) Math.round((mouseY - (cy + ch / 2)) / (double) CANVAS_CELL + camZ);
        moveVertex(draggingVertex, bx, bz);
        return true;
    }

    @Override
    protected boolean mouseReleasedScaled(int mouseX, int mouseY, int button) {
        draggingVertex = -1;
        return true;
    }

    @Override
    protected boolean mouseScrolledScaled(int mouseX, int mouseY, double deltaX, double deltaY) {
        camX -= deltaX * 16;
        camZ -= deltaY * 16;
        return true;
    }

    private int findVertexAt(int mx, int my, int centerX, int centerZ) {
        if (selectedPoint < 0) return -1;
        List<CapturePoint.Vertex> verts = serverPoints.get(selectedPoint).vertices();
        for (int i = 0; i < verts.size(); i++) {
            int vx = centerX + (int) ((verts.get(i).x() - camX) * CANVAS_CELL);
            int vz = centerZ + (int) ((verts.get(i).z() - camZ) * CANVAS_CELL);
            if (Math.abs(mx - vx) <= VERTEX_HIT_RADIUS && Math.abs(my - vz) <= VERTEX_HIT_RADIUS) return i;
        }
        return -1;
    }

    private void addVertex(int bx, int bz) {
        CapturePoint cp = serverPoints.get(selectedPoint);
        List<CapturePoint.Vertex> verts = new ArrayList<>(cp.vertices());
        verts.add(new CapturePoint.Vertex(bx, bz));
        serverPoints.set(selectedPoint, withVertices(cp, List.copyOf(verts)));
        sendVertices();
    }

    private void moveVertex(int index, int bx, int bz) {
        CapturePoint cp = serverPoints.get(selectedPoint);
        List<CapturePoint.Vertex> verts = new ArrayList<>(cp.vertices());
        if (index < 0 || index >= verts.size()) return;
        verts.set(index, new CapturePoint.Vertex(bx, bz));
        serverPoints.set(selectedPoint, withVertices(cp, List.copyOf(verts)));
        sendVertices();
    }

    private void sendVertices() {
        CapturePoint cp = serverPoints.get(selectedPoint);
        PjmNetworking.sendToServer(new CapturePointEditorActionPacket(
                CapturePointEditorActionPacket.Action.UPDATE_VERTICES,
                cp.id(), cp.displayName(), cp.dimension(), cp.ownerTeamId(), cp.vertices()));
    }

    private void handleButton(String id) {
        switch (id) {
            case "add" -> {
                String newId = "cp_" + (serverPoints.size() + 1);
                String dim = "minecraft:overworld";
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) dim = mc.player.level().dimension().location().toString();
                PjmNetworking.sendToServer(new CapturePointEditorActionPacket(
                        CapturePointEditorActionPacket.Action.ADD, newId, newId, dim, "", List.of()));
                serverPoints.add(new CapturePoint(newId, newId, dim, List.of(), "", "", 0, false));
                selectedPoint = serverPoints.size() - 1;
                if (nameBox != null) nameBox.setValue(newId);
            }
            case "remove" -> {
                if (selectedPoint < 0) return;
                CapturePoint cp = serverPoints.get(selectedPoint);
                PjmNetworking.sendToServer(new CapturePointEditorActionPacket(
                        CapturePointEditorActionPacket.Action.REMOVE, cp.id(), "", "", "", List.of()));
                serverPoints.remove(selectedPoint);
                selectedPoint = Math.min(selectedPoint, serverPoints.size() - 1);
                if (nameBox != null) nameBox.setValue(selectedPoint >= 0 ? serverPoints.get(selectedPoint).displayName() : "");
            }
            case "center" -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) { camX = mc.player.getX(); camZ = mc.player.getZ(); }
            }
        }
    }
}

