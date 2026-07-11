package ru.liko.pjmbasemod.client.capturepoint.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.event.DisplayUpdateEvent;
import journeymap.api.v2.client.event.FullscreenDisplayEvent;
import journeymap.api.v2.client.event.FullscreenMapEvent;
import journeymap.api.v2.client.event.FullscreenRenderEvent;
import journeymap.api.v2.client.event.PopupMenuEvent;
import journeymap.api.v2.client.fullscreen.IThemeButton;
import journeymap.api.v2.client.fullscreen.ModPopupMenu;
import journeymap.api.v2.client.fullscreen.ThemeButtonDisplay;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.capturepoint.ClientCapturePointState;
import ru.liko.pjmbasemod.client.gui.screen.CapturePointRenameScreen;
import ru.liko.pjmbasemod.common.capturepoint.CapturePoint;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.CapturePointEditorActionPacket;
import ru.liko.pjmbasemod.common.network.packet.CapturePointEditorActionPacket.Action;
import ru.liko.pjmbasemod.common.teams.Teams;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Редактирование точек захвата прямо на фуллскрин-карте JourneyMap, по модели
 * <a href="https://github.com/alejandrocoria/MapFrontiers">MapFrontiers</a>.
 *
 * <p><b>Модель взаимодействия</b> (только OP):
 * <ul>
 *   <li><b>Выбор ≠ правка.</b> ЛКМ по точке — выбрать её (подсветка). Кнопка
 *       «Редактировать» на тулбаре включает правку выбранной точки.</li>
 *   <li>В режиме правки: <b>ЛКМ</b> выбирает ближайшую вершину (в радиусе,
 *       зависящем от зума), <b>перетаскивание</b> двигает выбранную вершину,
 *       <b>Ctrl+перетаскивание</b> — двигает всю точку.</li>
 *   <li><b>ПКМ</b> — контекст-меню: создать точку / добавить вершину в ближайшее
 *       ребро / удалить вершину / владелец / переименовать / удалить / готово.</li>
 * </ul>
 *
 * <p>Правки применяются к локальной рабочей копии и <b>коммитятся одним пакетом</b>
 * {@link CapturePointEditorActionPacket} при выходе из правки (кнопка «Готово» или
 * закрытие карты), а не на каждый драг — так нет спама и лага от round-trip.</p>
 *
 * <p>Хит-тест ведётся в блочных координатах (радиус {@code max(2, 8192/zoom)}),
 * без пиксельных пересчётов — это устраняет ошибки ориентации. Пиксели нужны
 * только для отрисовки ручек вершин.</p>
 */
public final class CapturePointMapEditor {

    private static final String ICON_PATH = "textures/icon/";
    private static final ResourceLocation ICON_NEW = icon("target");
    private static final ResourceLocation ICON_EDIT = icon("personalization");
    private static final ResourceLocation ICON_RENAME = icon("menu");
    private static final ResourceLocation ICON_DELETE = icon("close");

    private static final int DEFAULT_HALF = 16;
    private static final int BANNER_COLOR = 0xFFFFC13D;
    private static final int NEUTRAL_COLOR = 0x9B9B9B;

    private final CapturePointJourneyMapRuntime runtime;
    private final IClientAPI api;

    // Кнопки тулбара — пересоздаются при каждом открытии карты (ADDON_BUTTON_DISPLAY_EVENT).
    @Nullable private IThemeButton buttonNew;
    @Nullable private IThemeButton buttonEdit;
    @Nullable private IThemeButton buttonRename;
    @Nullable private IThemeButton buttonDelete;

    @Nullable private String selectedId;
    @Nullable private CapturePoint selectedMeta;
    private final List<CapturePoint.Vertex> working = new ArrayList<>();
    private boolean editing;
    private boolean shapeDirty;
    private int selectedVertex = -1;
    @Nullable private BlockPos relocatePrev;

    private volatile long revision;

    CapturePointMapEditor(CapturePointJourneyMapRuntime runtime, IClientAPI api) {
        this.runtime = runtime;
        this.api = api;
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, ICON_PATH + name + ".png");
    }

    void registerEvents() {
        String id = Pjmbasemod.MODID;
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(id, this::onAddonButtons);
        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(id, this::onMapClick);
        FullscreenEventRegistry.FULLSCREEN_MAP_DRAG_EVENT.subscribe(id, this::onMapDrag);
        FullscreenEventRegistry.FULLSCREEN_POPUP_MENU_EVENT.subscribe(id, this::onPopup);
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(id, this::onRender);
        ClientEventRegistry.DISPLAY_UPDATE_EVENT.subscribe(id, this::onDisplayUpdate);
    }

    // ─────────────────────────── доступ для runtime ───────────────────────────

    long revision() {
        return revision;
    }

    @Nullable
    String selectedId() {
        return selectedId;
    }

    /** Вершины точки для отрисовки: рабочая копия для выбранной, иначе — с сервера. */
    List<CapturePoint.Vertex> effectiveVertices(String id, List<CapturePoint.Vertex> serverVertices) {
        if (id.equals(selectedId) && (editing || shapeDirty)) {
            return List.copyOf(working);
        }
        return serverVertices;
    }

    void onLogout() {
        resetState();
    }

    private void bump() {
        revision++;
        runtime.forceRebuild();
    }

    // ─────────────────────────── кнопки тулбара ───────────────────────────

    private void onAddonButtons(FullscreenDisplayEvent.AddonButtonDisplayEvent event) {
        if (!isEditor()) return;
        ThemeButtonDisplay display = event.getThemeButtonDisplay();
        buttonNew = display.addThemeButton(
                I18n.get("gui.pjmbasemod.capturepoint.button.new"), ICON_NEW, b -> createPointAtPlayer());
        buttonEdit = display.addThemeToggleButton(
                I18n.get("gui.pjmbasemod.capturepoint.button.done"),
                I18n.get("gui.pjmbasemod.capturepoint.button.edit"),
                ICON_EDIT, editing, b -> toggleEdit());
        buttonRename = display.addThemeButton(
                I18n.get("gui.pjmbasemod.capturepoint.button.rename"), ICON_RENAME, b -> openRename());
        buttonDelete = display.addThemeButton(
                I18n.get("gui.pjmbasemod.capturepoint.button.delete"), ICON_DELETE, b -> deleteSelected());
        updateButtons();
    }

    private void updateButtons() {
        if (buttonEdit == null) return;
        boolean hasSelection = selectedId != null;
        buttonNew.setEnabled(!editing);
        buttonEdit.setEnabled(hasSelection);
        buttonEdit.setToggled(editing);
        buttonRename.setEnabled(hasSelection && !editing);
        buttonDelete.setEnabled(hasSelection && !editing);
    }

    private void onDisplayUpdate(DisplayUpdateEvent event) {
        if (event.uiState.ui != Context.UI.Fullscreen) return;
        if (event.uiState.active) {
            updateButtons();
        } else {
            // Карта закрыта — коммитим правки и сбрасываем выбор.
            stopEditing();
            clearSelection();
        }
    }

    // ─────────────────────────── клик / драг / движение ───────────────────────────

    private void onMapClick(FullscreenMapEvent.ClickEvent event) {
        // ЛКМ обрабатываем на POST (после того как JM зафиксировал начало драга),
        // ПКМ — на PRE. Так задумано в MapFrontiers.
        FullscreenMapEvent.Stage relevant = event.getButton() == 1
                ? FullscreenMapEvent.Stage.PRE : FullscreenMapEvent.Stage.POST;
        if (event.getStage() != relevant) return;
        if (handleClick(event.getLevel(), event.getLocation(), event.getButton())) {
            event.cancel();
        }
    }

    private boolean handleClick(ResourceKey<Level> dimension, BlockPos pos, int button) {
        relocatePrev = null; // каждый новый драг начинается с клика
        UIState ui = fullscreenState();
        if (ui == null) return false;
        double maxDist = maxHitDistance(ui);

        if (editing && selectedId != null) {
            if (button == 0) {
                selectedVertex = closestVertex(pos, maxDist);
                bump();
            }
            return false; // не отменяем — драг сам решит, двигать вершину или панорамировать
        }

        if (button == 0) {
            select(pointAt(dimension, pos, maxDist));
        }
        return false;
    }

    private void onMapDrag(FullscreenMapEvent.MouseDraggedEvent event) {
        if (event.getStage() != FullscreenMapEvent.Stage.PRE) return;
        if (handleDrag(event.getLevel(), event.getLocation())) {
            event.cancel();
        }
    }

    private boolean handleDrag(ResourceKey<Level> dimension, BlockPos pos) {
        if (!editing || selectedId == null) return false;
        if (selectedMeta != null && !selectedMeta.dimension().equals(dimension.location().toString())) return false;

        if (Screen.hasControlDown()) {
            // Перемещение всей точки.
            if (relocatePrev != null) {
                int dx = pos.getX() - relocatePrev.getX();
                int dz = pos.getZ() - relocatePrev.getZ();
                if (dx != 0 || dz != 0) {
                    for (int i = 0; i < working.size(); i++) {
                        CapturePoint.Vertex v = working.get(i);
                        working.set(i, new CapturePoint.Vertex(v.x() + dx, v.z() + dz));
                    }
                    shapeDirty = true;
                    bump();
                }
            }
            relocatePrev = pos;
            return true;
        }

        if (selectedVertex >= 0 && selectedVertex < working.size()) {
            working.set(selectedVertex, new CapturePoint.Vertex(pos.getX(), pos.getZ()));
            shapeDirty = true;
            bump();
            return true;
        }
        return false; // вершина не выбрана — даём JM панорамировать карту
    }

    // ─────────────────────────── контекст-меню ───────────────────────────

    private void onPopup(PopupMenuEvent.FullscreenPopupMenuEvent event) {
        if (!isEditor()) return;
        ModPopupMenu menu = event.getPopupMenu();

        if (editing && selectedId != null) {
            menu.addMenuItem(I18n.get("gui.pjmbasemod.capturepoint.menu.add_vertex"), this::addVertexAt);
            if (selectedVertex >= 0) {
                menu.addMenuItem(I18n.get("gui.pjmbasemod.capturepoint.menu.remove_selected_vertex"),
                        p -> removeSelectedVertex());
            }
            addOwnerSubmenu(menu);
            menu.addMenuItem(I18n.get("gui.pjmbasemod.capturepoint.menu.rename"), p -> openRename());
            menu.addMenuItem(I18n.get("gui.pjmbasemod.capturepoint.menu.done"), p -> stopEditing());
            return;
        }

        menu.addMenuItem(I18n.get("gui.pjmbasemod.capturepoint.menu.create"), this::createPointAt);
        if (selectedId != null) {
            menu.addMenuItem(I18n.get("gui.pjmbasemod.capturepoint.menu.edit"), p -> startEditing());
            addOwnerSubmenu(menu);
            menu.addMenuItem(I18n.get("gui.pjmbasemod.capturepoint.menu.rename"), p -> openRename());
            menu.addMenuItem(I18n.get("gui.pjmbasemod.capturepoint.menu.delete"), p -> deleteSelected());
        }
    }

    private void addOwnerSubmenu(ModPopupMenu menu) {
        if (selectedMeta == null) return;
        ModPopupMenu sub = menu.createSubItemList(I18n.get("gui.pjmbasemod.capturepoint.menu.owner"));
        sub.addMenuItem(I18n.get("gui.pjmbasemod.capturepoint.menu.owner.neutral"), p -> setOwner(""));
        for (var team : Teams.all()) {
            String teamId = team.id();
            sub.addMenuItem(Teams.displayName(null, teamId), p -> setOwner(teamId));
        }
    }

    // ─────────────────────────── действия ───────────────────────────

    private void toggleEdit() {
        if (editing) stopEditing();
        else startEditing();
    }

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
        updateButtons();
        bump();
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
        updateButtons();
        bump();
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
        updateButtons();
        bump();
    }

    private void clearSelection() {
        selectedId = null;
        selectedMeta = null;
        working.clear();
        selectedVertex = -1;
        relocatePrev = null;
        updateButtons();
        bump();
    }

    private void resetState() {
        editing = false;
        shapeDirty = false;
        selectedId = null;
        selectedMeta = null;
        working.clear();
        selectedVertex = -1;
        relocatePrev = null;
    }

    private void createPointAtPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos pos = mc.player.blockPosition();
        createPoint(pos, mc.player.level().dimension().location().toString());
    }

    private void createPointAt(BlockPos pos) {
        UIState ui = fullscreenState();
        String dim = ui != null ? ui.dimension.location().toString()
                : Minecraft.getInstance().player.level().dimension().location().toString();
        createPoint(pos, dim);
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
        selectedMeta = new CapturePoint(id, id, dim, square, "", NEUTRAL_COLOR, "", 0, false);
        working.clear();
        working.addAll(square);
        editing = true;
        shapeDirty = false;
        selectedVertex = -1;
        relocatePrev = null;
        updateButtons();
        bump();
    }

    private void addVertexAt(BlockPos pos) {
        if (!editing || selectedId == null) return;
        insertVertexOnClosestEdge(pos);
        shapeDirty = true;
        bump();
    }

    private void removeSelectedVertex() {
        if (!editing || selectedVertex < 0 || selectedVertex >= working.size()) return;
        if (working.size() <= 3) return; // полигон не должен стать вырожденным
        working.remove(selectedVertex);
        selectedVertex = -1;
        shapeDirty = true;
        bump();
    }

    private void setOwner(String teamId) {
        if (selectedId == null || selectedMeta == null) return;
        send(Action.SET_OWNER, selectedId, selectedMeta.displayName(),
                selectedMeta.dimension(), teamId, selectedMeta.vertices());
        selectedMeta = withOwner(selectedMeta, teamId);
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
        if (editing) {
            editing = false;
            shapeDirty = false;
        }
        send(Action.REMOVE, selectedId, "", "", "", List.of());
        clearSelection();
    }

    private void send(Action action, String id, String name, String dim, String owner,
                      List<CapturePoint.Vertex> vertices) {
        PjmNetworking.sendToServer(new CapturePointEditorActionPacket(action, id, name, dim, owner, vertices));
    }

    // ─────────────────────────── рендер ───────────────────────────

    private void onRender(FullscreenRenderEvent event) {
        if (!editing || selectedId == null) return;
        // Полигон рисует сам JourneyMap (PolygonOverlay из рабочей копии в runtime).
        // Здесь — только подсказка-баннер, без ручной геометрии в пикселях.
        drawBanner(event.getGraphics());
    }

    private void drawBanner(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        String name = selectedMeta != null ? selectedMeta.displayName() : selectedId;
        String hint = I18n.get("gui.pjmbasemod.capturepoint.hint.edit", name);
        int tw = mc.font.width(hint);
        int cx = w / 2;
        g.fill(cx - tw / 2 - 6, 3, cx + tw / 2 + 6, 17, 0xB0000000);
        g.drawCenteredString(mc.font, hint, cx, 6, BANNER_COLOR);
    }

    // ─────────────────────────── геометрия ───────────────────────────

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

    /** Ближайшая (не в режиме правки) точка: содержащая позицию, либо с ближайшей вершиной. */
    @Nullable
    private String pointAt(ResourceKey<Level> dimension, BlockPos pos, double maxDist) {
        String dim = dimension.location().toString();
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

    // ─────────────────────────── утилиты ───────────────────────────

    private static boolean isEditor() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(2)
                && Config.isCapturePointsEnabled() && Config.isCapturePointJourneyMapEnabled();
    }

    @Nullable
    private UIState fullscreenState() {
        try {
            return api.getUIState(Context.UI.Fullscreen);
        } catch (Throwable t) {
            return null;
        }
    }

    private static double maxHitDistance(UIState ui) {
        return Math.max(2.0, 8192.0 / Math.max(1, ui.zoom));
    }

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
                owner, color, cp.captureTeamId(), cp.progressPercent(), cp.contested());
    }

    private static CapturePoint withName(CapturePoint cp, String name) {
        return new CapturePoint(cp.id(), name, cp.dimension(), cp.vertices(),
                cp.ownerTeamId(), cp.ownerColor(), cp.captureTeamId(), cp.progressPercent(), cp.contested());
    }
}
