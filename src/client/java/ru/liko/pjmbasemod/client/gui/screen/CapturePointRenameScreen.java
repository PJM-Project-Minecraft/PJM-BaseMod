package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Минимальный модальный экран ввода нового имени точки захвата. Вызывается из
 * контекст-меню JourneyMap-редактора; по закрытию возвращает игрока на карту.
 */
public class CapturePointRenameScreen extends Screen {

    private static final int FIELD_WIDTH = 220;

    @Nullable private final Screen parent;
    private final String current;
    private final Consumer<String> onConfirm;
    @Nullable private EditBox nameBox;

    public CapturePointRenameScreen(@Nullable Screen parent, String current, Consumer<String> onConfirm) {
        super(Component.translatable("gui.pjmbasemod.capturepoint.rename.title"));
        this.parent = parent;
        this.current = current;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        nameBox = new EditBox(this.font, cx - FIELD_WIDTH / 2, cy - 10, FIELD_WIDTH, 20,
                Component.translatable("gui.pjmbasemod.capturepoint.rename.title"));
        nameBox.setMaxLength(64);
        nameBox.setValue(current);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.pjmbasemod.capturepoint.rename.confirm"),
                        b -> confirm())
                .bounds(cx - FIELD_WIDTH / 2, cy + 18, FIELD_WIDTH / 2 - 4, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx + 4, cy + 18, FIELD_WIDTH / 2 - 4, 20).build());
    }

    private void confirm() {
        if (nameBox != null) {
            String value = nameBox.getValue().trim();
            if (!value.isEmpty()) onConfirm.accept(value);
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / Numpad Enter
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 34, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
