# GUI-экраны: руководство по разработке

Документ описывает, как делать полноэкранные `Screen`-меню мода (Garage, Warehouse, Faction* и т.п.) с корректным масштабированием, чтобы они **не вылезали за края экрана** и одинаково выглядели на любом разрешении.

> Касается только полноэкранных `Screen`. HUD-оверлеи (`client/gui/overlay/`) сюда не относятся — у них своя логика позиционирования.

## TL;DR

1. Наследуй `PjmBaseScreen`, а не `Screen`.
2. Передай в конструктор логический размер панели `(guiWidth, guiHeight)`.
3. Рисуй в `renderScaled()`, не в `render()`.
4. Обрабатывай мышь в `*Scaled`-методах (`mouseClickedScaled`, `mouseScrolledScaled`, ...).
5. Никогда не вызывай `renderBackground()` из `renderScaled()` — фон уже нарисован.
6. Бери цвета и утилиты из `PjmGuiUtils`, не хардкодь и не дублируй.

## Базовый класс `PjmBaseScreen`

Путь: `src/client/.../client/gui/screen/PjmBaseScreen.java`.

Решает главную проблему: при окне меньше, чем `guiWidth × guiHeight + отступы`, вся панель **масштабируется вниз** через `PoseStack`, никогда не вылезая за края. При большом окне масштаб фиксируется на `1.0` (панель не растягивается).

### Как это работает

```
render() [ванильный, экранные координаты]
 ├─ super.renderBackground()      // блюр + затемнение, БЕЗ масштаба, на весь экран
 └─ pose.scale(guiScale())        // применяем масштаб
     └─ renderScaled()            // ТВОЙ код — в виртуальных координатах
```

- `guiScale()` = `min(1.0, width/(guiWidth+16), height/(guiHeight+16))`.
- Виртуальное пространство = экран, поделённый на масштаб. То есть в `renderScaled()` ты всегда рисуешь так, будто экран минимум `guiWidth+16 × guiHeight+16` логических пикселей.
- Координаты мыши автоматически переводятся в виртуальные перед вызовом `*Scaled`-методов.

### Ключевые методы

| Метод | Назначение |
|-------|-----------|
| `renderScaled(g, mouseX, mouseY, tick)` | **abstract** — основной рендер. Координаты виртуальные, фон уже нарисован. |
| `mouseClickedScaled(mouseX, mouseY, btn)` | Клик, виртуальные координаты. |
| `mouseScrolledScaled(mouseX, mouseY, dx, dy)` | Скролл. |
| `mouseDraggedScaled(...)` / `mouseReleasedScaled(...)` | Перетаскивание / отпускание. |
| `guiLeft()` / `guiTop()` | Левый/верхний угол центрированной панели (виртуальные коорд.). |
| `vWidth()` / `vHeight()` | Размер виртуального экрана. |
| `vMouseX(d)` / `vMouseY(d)` | Ручной перевод экранных координат в виртуальные (нужен редко). |

## Шаблон нового экрана

```java
public class ExampleScreen extends PjmBaseScreen {

    private static final int GUI_WIDTH = 460;
    private static final int GUI_HEIGHT = 280;
    private static final int HEADER_HEIGHT = 22;
    private static final int SIDEBAR_WIDTH = 120;

    public ExampleScreen(/* snapshot */) {
        super(Component.translatable("gui.pjmbasemod.example.title"), GUI_WIDTH, GUI_HEIGHT);
    }

    public static void open(/* snapshot */) {
        Minecraft.getInstance().setScreen(new ExampleScreen(/* ... */));
    }

    @Override
    protected void renderScaled(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = guiLeft();
        int top  = guiTop();

        // Панель целиком (фон + рамка + хедер + сайдбар) одним вызовом:
        PjmGuiUtils.drawScreenPanel(g, left, top, GUI_WIDTH, GUI_HEIGHT, SIDEBAR_WIDTH, HEADER_HEIGHT);

        // Заголовок и кнопка закрытия
        g.drawString(font, getTitle(), left + 8, top + 7, PjmGuiUtils.TEXT_PRIMARY, false);

        // ... содержимое ...
    }

    @Override
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        if (button != 0) return super.mouseClickedScaled(mouseX, mouseY, button);
        int left = guiLeft();
        int top  = guiTop();

        // Кнопка закрытия в хедере
        if (mouseX >= left + GUI_WIDTH - 24 && mouseX <= left + GUI_WIDTH
                && mouseY >= top && mouseY <= top + HEADER_HEIGHT) {
            PjmUiSounds.playClick();
            onClose();
            return true;
        }
        // ... остальные зоны ...
        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // не ставить игру на паузу
    }
}
```

## Утилиты `PjmGuiUtils`

Путь: `src/client/.../client/gui/PjmGuiUtils.java`. Всё общее — здесь. **Не дублируй эти методы в экранах.**

### Цвета (палитра экранов)

| Константа | Назначение |
|-----------|-----------|
| `SCREEN_BG` | Фон панели |
| `SCREEN_BORDER` | Рамка |
| `SCREEN_HEADER` | Фон хедера |
| `SCREEN_SIDEBAR` | Фон сайдбара |
| `SCREEN_ROW` / `SCREEN_ROW_HOVER` / `SCREEN_ROW_LOCKED` | Строки списков |
| `SCREEN_SELECT` | Выделение (синее) |
| `SCREEN_SCRIM` | Затемнение поверх блюра |
| `TEXT_PRIMARY` / `TEXT_DIM` / `TEXT_MUTED` / `TEXT_LABEL` / `TEXT_GOLD` | Текст |

### Методы

| Метод | Назначение |
|-------|-----------|
| `drawScreenPanel(g, x, y, w, h, sidebarW, headerH)` | Панель целиком: фон + рамка + хедер + сайдбар. |
| `drawBorder(g, x, y, w, h, color)` | Внешняя рамка 1px. |
| `drawSmoothIcon(g, icon, x, y, w, h[, alpha])` | Иконка с билинейной фильтрацией (без пикселизации). |
| `ellipsize(font, text, maxWidth)` | Обрезка строки с «...». |
| `drawScrollbar(g, x, y, height, total, visible, scroll)` | Вертикальный скроллбар. |
| `withAlpha(color, alpha)` | Задать альфу цвета. |
| `lerpColor(from, to, t)` | Интерполяция ARGB. |

## Правила и частые ошибки

### ✅ Делай

- **Все размеры в `renderScaled()`** считай от `guiLeft()`/`guiTop()` + констант `GUI_WIDTH`/`GUI_HEIGHT`. Не используй `width`/`height` напрямую внутри `renderScaled()` для позиционирования панели.
- **Для фона на весь виртуальный экран** (затемнение под панелью) используй `vWidth()`/`vHeight()`, а не `width`/`height`.
- **Зоны клика вычисляй ОДНИМ источником.** Не дублируй формулы координат кнопок в `renderScaled()` и `mouseClickedScaled()` — они разъедутся, и кнопка перестанет нажиматься там, где нарисована. Вынеси зону в общий метод, возвращающий `Rect` (см. ниже).
- **`isPauseScreen()` → `false`** для внутриигровых меню.

### ❌ Не делай

- **Не вызывай `renderBackground()` из `renderScaled()`.** Фон уже нарисован в `render()` до масштабирования. Повторный вызов — no-op (мы его специально заглушили), но это вводит в заблуждение. Если нужно затемнение — рисуй `g.fill(0, 0, vWidth(), vHeight(), SCREEN_SCRIM)`.
- **Не переопределяй `render()` или `renderBackground()`** в наследниках — это ломает конвейер масштабирования. Только `renderScaled()`.
- **Не переопределяй `mouseClicked()`/`mouseScrolled()`** напрямую — используй `*Scaled`-версии, иначе координаты мыши будут в экранном, а не виртуальном пространстве.
- **Не хардкодь цвета** вроде `0xF216161A` — бери из `PjmGuiUtils`.
- **Не используй `width`/`height`** для центрирования панели внутри `renderScaled()` — там это уже виртуальный масштаб, нужны `vWidth()`/`vHeight()` (или просто `guiLeft()`/`guiTop()`).

## Единый источник хит-зон (паттерн `Rect`)

Главная причина «кнопка не нажимается там, где нарисована» — координаты кнопки посчитаны **дважды**: отдельно в рендере и отдельно в обработчике клика. При любой правке отступа они разъезжаются.

Решение — один метод на зону, возвращающий прямоугольник, который используют **и** рендер, **и** клик:

```java
/** Прямоугольник в виртуальных координатах с проверкой попадания мыши. */
private record Rect(int x, int y, int w, int h) {
    boolean contains(double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}

private Rect closeRect() {
    return new Rect(guiLeft() + GUI_WIDTH - 30, guiTop(), 30, HEADER_HEIGHT);
}
```

```java
// в renderScaled():
boolean hoverClose = closeRect().contains(mouseX, mouseY);
// в mouseClickedScaled():
if (closeRect().contains(mouseX, mouseY)) { onClose(); return true; }
```

Для зон, зависящих от строки списка, передавай y строки параметром: `craftButtonRect(int rowY)`, `withdrawRect(int rowY)` и т.п. Примеры — `GarageScreen` (`tabRect`, `storeButtonRect`, `craftButtonRect`, `spawnButtonRect`, `recycleButtonRect`) и `WarehouseScreen` (`withdrawRect`, `depositRect`).

## Модальные оверлеи внутри экрана

Если поверх экрана нужно нарисовать модальное меню (как меню выбора точки спавна в `GarageScreen`):

- Рисуй его в конце `renderScaled()` (после основного контента) или вместо контента (`return` после отрисовки оверлея).
- **Координаты — строго виртуальные.** Центрируй через `vWidth()`/`vHeight()`, НЕ через `width`/`height`. Это была реальная бага: оверлеи Garage центрировались по `width`/`height`, и при масштабе ≠ 1.0 рисовались в одном месте, а кликались в другом.
- Затемняй фон под оверлеем: `g.fill(0, 0, vWidth(), vHeight(), 0xB0000000)`.
- Клики оверлея обрабатывай в начале `mouseClickedScaled()` с ранним `return`.

## Эталонные примеры

- **`GarageScreen`** — вкладки, список, скроллбар, 3D-превью сущности, модальные оверлеи.
- **`WarehouseScreen`** — сайдбар категорий, список с двумя кнопками на строку, панель очков.
- **`FactionSelectionScreen`** — две колонки (фракции/роли), hover-анимации, затемняющий скрим.
- **`FactionManagementScreen`** — список участников + панель ролей.
