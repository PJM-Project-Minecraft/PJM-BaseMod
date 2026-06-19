package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Базовый класс для всех GUI-экранов мода.
 *
 * Решает главную проблему: при маленьком окне (меньше GUI_WIDTH×GUI_HEIGHT + отступы)
 * панель масштабируется вниз, никогда не вылезая за края. При большом окне масштаб = 1.0
 * (не растягивается).
 *
 * Архитектура:
 *  - render() применяет PoseStack-масштаб и пробрасывает управление в renderScaled()
 *  - все mouse-методы прозрачно пересчитывают координаты в виртуальное пространство
 *  - наследники реализуют renderScaled() вместо render(), всё остальное — как обычно
 *
 * Координаты в renderScaled() и mouse-методах — виртуальные (логические пиксели),
 * то есть такие же, как если бы экран всегда был не меньше (GUI_WIDTH+padding)×(GUI_HEIGHT+padding).
 */
public abstract class PjmBaseScreen extends Screen {

    /** Минимальный отступ (в виртуальных пикселях) от края экрана с каждой стороны. */
    private static final int PADDING = 8;

    protected final int guiWidth;
    protected final int guiHeight;

    protected PjmBaseScreen(Component title, int guiWidth, int guiHeight) {
        super(title);
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
    }

    // -------------------------------------------------------------------------
    // Масштаб и виртуальные координаты
    // -------------------------------------------------------------------------

    /**
     * Масштаб: не превышает 1.0 (не растягиваем), но уменьшается если окно меньше GUI + padding.
     */
    protected float guiScale() {
        float sx = (float) width  / (guiWidth  + PADDING * 2);
        float sy = (float) height / (guiHeight + PADDING * 2);
        return Math.min(1.0f, Math.min(sx, sy));
    }

    /** Виртуальная ширина экрана в логических пикселях. */
    protected int vWidth() {
        return (int) Math.ceil(width / guiScale());
    }

    /** Виртуальная высота экрана в логических пикселях. */
    protected int vHeight() {
        return (int) Math.ceil(height / guiScale());
    }

    /** Левый край панели в виртуальных координатах (центрировано). */
    protected int guiLeft() {
        return (vWidth() - guiWidth) / 2;
    }

    /** Верхний край панели в виртуальных координатах (центрировано). */
    protected int guiTop() {
        return (vHeight() - guiHeight) / 2;
    }

    /** Экранные координаты мыши → виртуальные. */
    protected int vMouseX(double mouseX) {
        return (int) (mouseX / guiScale());
    }

    protected int vMouseY(double mouseY) {
        return (int) (mouseY / guiScale());
    }

    // -------------------------------------------------------------------------
    // Screen overrides — применяют масштаб и делегируют в *Scaled-методы
    // -------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. Фон (блюр, затемнение) — рисуется без нашего масштаба, покрывает весь экран.
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        // 2. Масштабированный контент панели.
        float scale = guiScale();
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        renderScaled(graphics, vMouseX(mouseX), vMouseY(mouseY), partialTick);
        graphics.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Переопределяем чтобы наследники, случайно вызвавшие renderBackground из renderScaled,
        // не сломали масштаб. Ничего не делаем — фон рисуется в render() до масштабирования.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClickedScaled(vMouseX(mouseX), vMouseY(mouseY), button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        return mouseScrolledScaled(vMouseX(mouseX), vMouseY(mouseY), deltaX, deltaY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        float scale = guiScale();
        return mouseDraggedScaled(vMouseX(mouseX), vMouseY(mouseY), button, dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return mouseReleasedScaled(vMouseX(mouseX), vMouseY(mouseY), button);
    }

    // -------------------------------------------------------------------------
    // Методы для переопределения в наследниках
    // -------------------------------------------------------------------------

    /**
     * Основной рендер экрана. Координаты уже в виртуальном пространстве.
     * Реализуй вместо render(). Фон (блюр) уже нарисован до вызова этого метода.
     */
    protected abstract void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /** Обработка клика. Координаты в виртуальном пространстве. */
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Обработка скролла. Координаты в виртуальном пространстве. */
    protected boolean mouseScrolledScaled(int mouseX, int mouseY, double deltaX, double deltaY) {
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    /** Обработка перетаскивания. Координаты в виртуальном пространстве. */
    protected boolean mouseDraggedScaled(int mouseX, int mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** Обработка отпускания кнопки. Координаты в виртуальном пространстве. */
    protected boolean mouseReleasedScaled(int mouseX, int mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
