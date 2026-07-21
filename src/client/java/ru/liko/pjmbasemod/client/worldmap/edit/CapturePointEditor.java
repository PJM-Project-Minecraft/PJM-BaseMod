package ru.liko.pjmbasemod.client.worldmap.edit;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import ru.liko.pjmbasemod.client.capturepoint.ClientCapturePointState;
import ru.liko.pjmbasemod.client.gui.screen.CapturePointRenameScreen;
import ru.liko.pjmbasemod.client.worldmap.gui.MapRenderer;
import ru.liko.pjmbasemod.common.capturepoint.CapturePoint;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.CapturePointEditorActionPacket;
import ru.liko.pjmbasemod.common.network.packet.CapturePointEditorActionPacket.Action;
import ru.liko.pjmbasemod.common.teams.Teams;

/**
 * OP-редактор точек захвата на собственной карте (порт логики JM-редактора, отвязанный от
 * JourneyMap). Хит-тест в блочных координатах; коммит одним {@link CapturePointEditorActionPacket}
 * при выходе из правки/закрытии карты. Синглтон — состояние переживает открытие под-экрана
 * (переименование) и повторное открытие карты.
 */
public final class CapturePointEditor {

    private static final CapturePointEditor INSTANCE = new CapturePointEditor();

    public static CapturePointEditor get() {
        return INSTANCE;
    }

    private static final int DEFAULT_HALF = 16;
    private static final int NEUTRAL_COLOR = 0x9B9B9B;
    private static final int BANNER_COLOR = 0xFFFFC13D;
    private static final String L = "gui.pjmbasemod.capturepoint.";

    private boolean enabled;
    @Nullable private String selectedId;
    @Nullable private CapturePoint selectedMeta;
    private final List<CapturePoint.Vertex> working = new ArrayList<>();
    private boolean editing;
    private boolean shapeDirty;
    private int selectedVertex = -1;
    @Nullable private BlockPos relocatePrev;

    private CapturePointEditor() {}

    // ─── режим ───

    public boolean enabled() {
        return enabled;
    }

    public void toggleEnabled() {
        enabled = !enabled;
        if (!enabled) commitAndDeselect();
    }

    @Nullable
    public String selectedId() {
        return selectedId;
    }

    public void commitAndDeselect() {
        stopEditing();
        clearSelection();
    }

    public void reset() {
        enabled = false;
        editing = false;
        shapeDirty = false;
        selectedId = null;
        selectedMeta = null;
        working.clear();
        selectedVertex = -1;
        relocatePrev = null;
    }

    public void onLogout() {
        reset();
    }

    // ─── ввод от MapScreen (блочные координаты) ───

    public void handleLeftClick(BlockPos pos, String dim, double maxDist) {
        relocatePrev = null;
        if (editing && selectedId != null) {
            selectedVertex = closestVertex(pos, maxDist);
            return;
        }
        select(pointAt(dim, pos, maxDist));
    }

    /** @return true если драг что-то подвинул (MapScreen не должен панорамировать). */
    public boolean handleDragTo(BlockPos pos, String dim, boolean ctrl) {
        if (!editing || selectedId == null) return false;
        if (selectedMeta != null && !selectedMeta.dimension().equals(dim)) return false;
        if (ctrl) {
            if (relocatePrev != null) {
                int dx = pos.getX() - relocatePrev.getX();
                int dz = pos.getZ() - relocatePrev.getZ();
                if (dx != 0 || dz != 0) {
                    for (int i = 0; i < working.size(); i++) {
                        CapturePoint.Vertex v = working.get(i);
                        working.set(i, new CapturePoint.Vertex(v.x() + dx, v.z() + dz));
                    }
                    shapeDirty = true;
                }
            }
            relocatePrev = pos;
            return true;
        }
        if (selectedVertex >= 0 && selectedVertex < working.size()) {
            working.set(selectedVertex, new CapturePoint.Vertex(pos.getX(), pos.getZ()));
            shapeDirty = true;
            return true;
        }
        return false;
    }

    public void endDrag() {
        relocatePrev = null;
    }

    // ─── контекст-меню ───

    public List<MapContextMenu.Entry> contextEntries(BlockPos pos, String dim) {
        List<MapContextMenu.Entry> items = new ArrayList<>();
        if (editing && selectedId != null) {
            items.add(leaf("menu.add_vertex", () -> addVertexAt(pos)));
            if (selectedVertex >= 0) items.add(leaf("menu.remove_selected_vertex", this::removeSelectedVertex));
            addOwnerEntries(items);
            addOrderEntries(items);
            items.add(leaf("menu.rename", this::openRename));
            items.add(leaf("menu.done", this::stopEditing));
            return items;
        }
        items.add(leaf("menu.create", () -> createPoint(pos, dim)));
        if (selectedId != null) {
            items.add(leaf("menu.edit", this::startEditing));
            addOwnerEntries(items);
            addOrderEntries(items);
            items.add(leaf("menu.rename", this::openRename));
            items.add(leaf("menu.delete", this::deleteSelected));
        }
        return items;
    }

    private MapContextMenu.Entry leaf(String key, Runnable action) {
        return MapContextMenu.Entry.leaf(I18n.get(L + key), action);
    }

    private void addOwnerEntries(List<MapContextMenu.Entry> items) {
        if (selectedMeta == null) return;
        List<MapContextMenu.Entry> sub = new ArrayList<>();
        sub.add(MapContextMenu.Entry.leaf(I18n.get(L + "menu.owner.neutral"), () -> setOwner("")));
        for (var team : Teams.all()) {
            String teamId = team.id();
            sub.add(MapContextMenu.Entry.leaf(Teams.displayName(null, teamId), () -> setOwner(teamId)));
        }
        items.add(MapContextMenu.Entry.sub(I18n.get(L + "menu.owner"), sub));
    }

    private void addOrderEntries(List<MapContextMenu.Entry> items) {
        if (selectedMeta == null) return;
        int count = 0;
        for (CapturePoint cp : ClientCapturePointState.points()) {
            if (cp.dimension().equals(selectedMeta.dimension())) count++;
        }
        List<MapContextMenu.Entry> sub = new ArrayList<>();
        for (int i = 1; i <= Math.max(count, 1); i++) {
            int order = i;
            String label = order == selectedMeta.order() ? "✔ " + order : String.valueOf(order);
            sub.add(MapContextMenu.Entry.leaf(label, () -> setOrder(order)));
        }
        sub.add(MapContextMenu.Entry.leaf(I18n.get(L + "menu.order.none"), () -> setOrder(0)));
        items.add(MapContextMenu.Entry.sub(I18n.get(L + "menu.order", selectedMeta.order()), sub));
    }

    // ─── действия ───

    private void startEditing() {
        if (selectedId == null) return;
        CapturePoint cp = findPoint(selectedId);
        if (cp == null) return;
        selectedMeta = cp;
        working.clear();
        working.addAll(cp.vertices());
        editing = true;
        shapeDirty = false;
        selectedVertex = -1;
        relocatePrev = null;
    }

    private void stopEditing() {
        if (!editing) return;
        editing = false;
        relocatePrev = null;
        selectedVertex = -1;
        if (shapeDirty && selectedId != null && selectedMeta != null) {
            send(Action.UPDATE_VERTICES, selectedId, selectedMeta.displayName(),
                    selectedMeta.dimension(), selectedMeta.ownerTeamId(), List.copyOf(working));
        }
        shapeDirty = false;
    }

    private void select(@Nullable String id) {
        if (editing) stopEditing();
        if (id == null) {
            clearSelection();
            return;
        }
        CapturePoint cp = findPoint(id);
        if (cp == null) {
            clearSelection();
            return;
        }
        selectedId = id;
        selectedMeta = cp;
        working.clear();
        working.addAll(cp.vertices());
        selectedVertex = -1;
    }

    private void clearSelection() {
        selectedId = null;
        selectedMeta = null;
        working.clear();
        selectedVertex = -1;
        relocatePrev = null;
    }

    private void createPoint(BlockPos center, String dim) {
        String id = nextPointId();
        List<CapturePoint.Vertex> square = List.of(
                new CapturePoint.Vertex(center.getX() - DEFAULT_HALF, center.getZ() - DEFAULT_HALF),
                new CapturePoint.Vertex(center.getX() + DEFAULT_HALF, center.getZ() - DEFAULT_HALF),
                new CapturePoint.Vertex(center.getX() + DEFAULT_HALF, center.getZ() + DEFAULT_HALF),
                new CapturePoint.Vertex(center.getX() - DEFAULT_HALF, center.getZ() + DEFAULT_HALF));
        send(Action.ADD, id, id, dim, "", List.of());
        send(Action.UPDATE_VERTICES, id, id, dim, "", square);

        selectedId = id;
        selectedMeta = new CapturePoint(id, id, dim, square, "", NEUTRAL_COLOR, "", 0, false, 0);
        working.clear();
        working.addAll(square);
        editing = true;
        shapeDirty = false;
        selectedVertex = -1;
        relocatePrev = null;
    }

    private void addVertexAt(BlockPos pos) {
        if (!editing || selectedId == null) return;
        insertVertexOnClosestEdge(pos);
        shapeDirty = true;
    }

    private void removeSelectedVertex() {
        if (!editing || selectedVertex < 0 || selectedVertex >= working.size()) return;
        if (working.size() <= 3) return; // полигон не должен вырождаться
        working.remove(selectedVertex);
        selectedVertex = -1;
        shapeDirty = true;
    }

    private void setOwner(String teamId) {
        if (selectedId == null || selectedMeta == null) return;
        send(Action.SET_OWNER, selectedId, selectedMeta.displayName(),
                selectedMeta.dimension(), teamId, selectedMeta.vertices());
        selectedMeta = withOwner(selectedMeta, teamId);
    }

    private void setOrder(int order) {
        if (selectedId == null || selectedMeta == null) return;
        PjmNetworking.sendToServer(new CapturePointEditorActionPacket(Action.SET_ORDER, selectedId,
                selectedMeta.displayName(), selectedMeta.dimension(), selectedMeta.ownerTeamId(),
                selectedMeta.vertices(), order));
        selectedMeta = withOrder(selectedMeta, order);
    }

    private void openRename() {
        if (selectedId == null || selectedMeta == null) return;
        Minecraft mc = Minecraft.getInstance();
        Screen parent = mc.screen;
        String id = selectedId;
        String dim = selectedMeta.dimension();
        String owner = selectedMeta.ownerTeamId();
        List<CapturePoint.Vertex> verts = selectedMeta.vertices();
        mc.setScreen(new CapturePointRenameScreen(parent, selectedMeta.displayName(), name -> {
            send(Action.UPDATE_DISPLAY_NAME, id, name, dim, owner, verts);
            if (id.equals(selectedId) && selectedMeta != null) {
                selectedMeta = withName(selectedMeta, name);
            }
        }));
    }

    private void deleteSelected() {
        if (selectedId == null) return;
        editing = false;
        shapeDirty = false;
        send(Action.REMOVE, selectedId, "", "", "", List.of());
        clearSelection();
    }

    private void send(Action action, String id, String name, String dim, String owner,
                      List<CapturePoint.Vertex> vertices) {
        PjmNetworking.sendToServer(new CapturePointEditorActionPacket(action, id, name, dim, owner, vertices, 0));
    }

    // ─── рендер ───

    public void render(GuiGraphics gg, Font font, double camX, double camZ, double scale, int width, int height) {
        if (selectedId == null) return;
        List<CapturePoint.Vertex> verts = (editing || shapeDirty)
                ? working
                : (selectedMeta != null ? selectedMeta.vertices() : working);
        if (verts.isEmpty()) return;
        int rgb = (selectedMeta != null
                ? (selectedMeta.ownerTeamId().isEmpty() ? NEUTRAL_COLOR : selectedMeta.ownerColor())
                : 0xFFFFFF) & 0xFFFFFF;
        int argb = 0xFF000000 | rgb;
        float th = editing ? 2.5f : 2f;
        for (int i = 0; i < verts.size(); i++) {
            CapturePoint.Vertex a = verts.get(i);
            CapturePoint.Vertex b = verts.get((i + 1) % verts.size());
            double ax = MapRenderer.worldToScreenX(a.x() + 0.5, camX, scale, width);
            double ay = MapRenderer.worldToScreenY(a.z() + 0.5, camZ, scale, height);
            double bx = MapRenderer.worldToScreenX(b.x() + 0.5, camX, scale, width);
            double by = MapRenderer.worldToScreenY(b.z() + 0.5, camZ, scale, height);
            MapRenderer.line(gg, ax, ay, bx, by, th, argb);
        }
        if (editing) {
            for (int i = 0; i < verts.size(); i++) {
                CapturePoint.Vertex v = verts.get(i);
                int hx = (int) MapRenderer.worldToScreenX(v.x() + 0.5, camX, scale, width);
                int hy = (int) MapRenderer.worldToScreenY(v.z() + 0.5, camZ, scale, height);
                boolean sel = i == selectedVertex;
                int s = sel ? 4 : 3;
                gg.fill(hx - s, hy - s, hx + s, hy + s, 0xFF000000);
                gg.fill(hx - s + 1, hy - s + 1, hx + s - 1, hy + s - 1, sel ? 0xFFFFDD33 : 0xFFFFFFFF);
            }
        }
        String name = selectedMeta != null ? selectedMeta.displayName() : selectedId;
        String hint = editing ? I18n.get(L + "hint.edit", name) : I18n.get(L + "hint.selected", name);
        int tw = font.width(hint);
        int cx = width / 2;
        gg.fill(cx - tw / 2 - 6, 20, cx + tw / 2 + 6, 34, 0xB0000000);
        gg.drawCenteredString(font, hint, cx, 23, BANNER_COLOR);
    }

    // ─── геометрия ───

    private int closestVertex(BlockPos pos, double maxDist) {
        int best = -1;
        double bestSq = maxDist * maxDist;
        for (int i = 0; i < working.size(); i++) {
            double dx = working.get(i).x() - pos.getX();
            double dz = working.get(i).z() - pos.getZ();
            double d = dx * dx + dz * dz;
            if (d <= bestSq) {
                bestSq = d;
                best = i;
            }
        }
        return best;
    }

    @Nullable
    private String pointAt(String dim, BlockPos pos, double maxDist) {
        String nearest = null;
        double nearestSq = maxDist * maxDist;
        for (CapturePoint cp : ClientCapturePointState.points()) {
            if (!cp.dimension().equals(dim)) continue;
            if (CapturePoint.contains(cp.vertices(), pos.getX(), pos.getZ())) {
                return cp.id();
            }
            for (CapturePoint.Vertex v : cp.vertices()) {
                double dx = v.x() - pos.getX();
                double dz = v.z() - pos.getZ();
                double d = dx * dx + dz * dz;
                if (d <= nearestSq) {
                    nearestSq = d;
                    nearest = cp.id();
                }
            }
        }
        return nearest;
    }

    private void insertVertexOnClosestEdge(BlockPos pos) {
        int n = working.size();
        if (n < 2) {
            working.add(new CapturePoint.Vertex(pos.getX(), pos.getZ()));
            selectedVertex = working.size() - 1;
            return;
        }
        int bestEdge = 0;
        double bestSq = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            CapturePoint.Vertex a = working.get(i);
            CapturePoint.Vertex b = working.get((i + 1) % n);
            double d = distToSegmentSq(pos.getX(), pos.getZ(), a.x(), a.z(), b.x(), b.z());
            if (d < bestSq) {
                bestSq = d;
                bestEdge = i;
            }
        }
        working.add(bestEdge + 1, new CapturePoint.Vertex(pos.getX(), pos.getZ()));
        selectedVertex = bestEdge + 1;
    }

    private static double distToSegmentSq(int px, int pz, int ax, int az, int bx, int bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double len2 = dx * dx + dz * dz;
        double t = len2 == 0 ? 0 : ((px - ax) * dx + (pz - az) * dz) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * dx;
        double cz = az + t * dz;
        double ex = px - cx;
        double ez = pz - cz;
        return ex * ex + ez * ez;
    }

    // ─── утилиты ───

    @Nullable
    private CapturePoint findPoint(String id) {
        for (CapturePoint cp : ClientCapturePointState.points()) {
            if (cp.id().equals(id)) return cp;
        }
        return null;
    }

    private String nextPointId() {
        List<CapturePoint> points = ClientCapturePointState.points();
        int i = points.size() + 1;
        while (true) {
            String candidate = "cp_" + i;
            boolean taken = candidate.equalsIgnoreCase(selectedId);
            for (CapturePoint cp : points) {
                if (cp.id().equalsIgnoreCase(candidate)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) return candidate;
            i++;
        }
    }

    private static CapturePoint withOwner(CapturePoint cp, String owner) {
        int color = owner.isEmpty() ? NEUTRAL_COLOR : Teams.color(null, owner);
        return new CapturePoint(cp.id(), cp.displayName(), cp.dimension(), cp.vertices(),
                owner, color, cp.captureTeamId(), cp.progressPercent(), cp.contested(), cp.order());
    }

    private static CapturePoint withName(CapturePoint cp, String name) {
        return new CapturePoint(cp.id(), name, cp.dimension(), cp.vertices(),
                cp.ownerTeamId(), cp.ownerColor(), cp.captureTeamId(), cp.progressPercent(), cp.contested(), cp.order());
    }

    private static CapturePoint withOrder(CapturePoint cp, int order) {
        return new CapturePoint(cp.id(), cp.displayName(), cp.dimension(), cp.vertices(),
                cp.ownerTeamId(), cp.ownerColor(), cp.captureTeamId(), cp.progressPercent(), cp.contested(), order);
    }
}
