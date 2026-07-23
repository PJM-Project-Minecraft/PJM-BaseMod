# Реализм ракет: план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Квазибаллистическая траектория (вертикальный старт → парабола → доворот 80°), pop-up фазы крылатых ракет, живой трек ракеты на карте для команды пуска.

**Architecture:** Позиция ракеты остаётся чистой функцией `elapsedTicks` (детерминизм, устойчивость к фризам чанков). Вся новая математика — в `BallisticTrajectory` (без MC-зависимостей, юнит-тесты). Трек — новый S→C пакет по шаблону подсистем (PjmNetworking → ClientPacketProxy → ClientPacketHandlersImpl → ClientMissileState → MapOverlays).

**Tech Stack:** Java 21, NeoForge 21.1.172, JUnit 5 (`src/test`), Gson-модели JSON-профилей.

**Спека:** `docs/superpowers/specs/2026-07-23-missile-realism-design.md`

## Global Constraints

- Проверка сборки: `./gradlew compileJava` + `./gradlew compileClientJava` (runClient/runServer из этого каталога НЕ работают — `!` в пути).
- `src/main` НИКОГДА не импортирует `src/client`.
- Новый S→C пакет: record в `common/network/packet/`, регистрация в `PjmNetworking`, бамп `VERSION` `"56"` → `"57"`, default-метод в `ClientPacketProxy`, реализация в `ClientPacketHandlersImpl`.
- Язык комментариев — русский. Локализация не нужна (стрелка без текста).
- Коммит после каждой задачи.

---

### Task 1: Квазибаллистический профиль в BallisticTrajectory (TDD)

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/missile/BallisticTrajectory.java`
- Modify: `src/test/java/ru/liko/pjmbasemod/common/missile/BallisticTrajectoryTest.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/entity/StrategicMissileEntity.java` (метод `ballisticPosition`)

**Interfaces:**
- Produces: `BallisticTrajectory.Sample(double horizontalFraction, double altitude)`; `static Sample sample(double progress, double startY, double targetY, double horizontalDistance, double apexHeight)`. Старый `altitudeAt(...)` удаляется (единственный вызов — `ballisticPosition`).

**Математика (замкнутые формы, C1-непрерывность):**
- Константы: `β = BOOST_HORIZONTAL_FRACTION = 0.06` (доля горизонтали на буст), `t_b = 2β/(1+β)` (доля времени на буст — из непрерывности горизонтальной скорости `2β/t_b = (1−β)/(1−t_b)`), `BOOST_CLIMB_FRACTION = 0.25`.
- Тайм-варп: при `t < t_b`: `u = β·(t/t_b)²` (горизонтальная скорость растёт от 0); дальше `u = β + (1−β)·(t−t_b)/(1−t_b)` (постоянная).
- Буст (u ∈ [0, β]): квадратичная Безье по `w = √(u/β)`: `P0=(0, startY)`, `P1=(0, controlY)`, `P2=(β, burnoutY)`, где `burnoutY = startY + 0.25·(apexY − startY)`; касательная в конце совпадает с параболой: `slopeU = 2·(apexY − burnoutY)/(diveStart − β)`, `controlY = max(startY + 1, burnoutY − slopeU·β)`. Касательная в P0 вертикальна (P1.x = P0.x) → старт строго вверх.
- Парабола (u ∈ [β, diveStart]): `y = apexY − (apexY − burnoutY)·(1−w)²`, `w = (u−β)/(diveStart−β)` — замедляющийся набор, наклон в апогее 0.
- Пикирование (u ≥ diveStart): существующие формулы transition+linear 80° без изменений (`diveStart = 1 − diveDistance/distance`, `diveDistance = drop/(IMPACT_SLOPE·0.875)`).
- Короткий маршрут (`diveStart ≤ β + 0.05`): существующий `hermiteFallback`, `horizontalFraction = t`.

- [ ] **Step 1: Переписать тесты (падающие)**

Полное содержимое `src/test/java/ru/liko/pjmbasemod/common/missile/BallisticTrajectoryTest.java`:

```java
package ru.liko.pjmbasemod.common.missile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BallisticTrajectoryTest {

    private static final double START_Y = 110.0;
    private static final double TARGET_Y = 70.0;
    private static final double DISTANCE = 2500.0;
    private static final double APEX_HEIGHT = 500.0;
    private static final double APEX_Y = START_Y + APEX_HEIGHT;

    private static BallisticTrajectory.Sample at(double t) {
        return BallisticTrajectory.sample(t, START_Y, TARGET_Y, DISTANCE, APEX_HEIGHT);
    }

    @Test
    void keepsStartAndTarget() {
        assertEquals(START_Y, at(0.0).altitude(), 1.0E-9);
        assertEquals(0.0, at(0.0).horizontalFraction(), 1.0E-9);
        assertEquals(TARGET_Y, at(1.0).altitude(), 1.0E-9);
        assertEquals(1.0, at(1.0).horizontalFraction(), 1.0E-9);
    }

    @Test
    void reachesConfiguredApex() {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 4000; i++) max = Math.max(max, at(i / 4000.0).altitude());
        assertEquals(APEX_Y, max, 1.0);
    }

    @Test
    void startsVertically() {
        BallisticTrajectory.Sample early = at(0.01);
        double horizontal = early.horizontalFraction() * DISTANCE;
        double climb = early.altitude() - START_Y;
        assertTrue(climb > 0.0);
        double angle = Math.toDegrees(Math.atan2(climb, horizontal));
        assertTrue(angle > 75.0, "старт должен быть почти вертикальным, а не " + angle + "°");
    }

    @Test
    void entersTargetAtEightyDegrees() {
        BallisticTrajectory.Sample before = at(1.0 - 1.0 / 220.0);
        BallisticTrajectory.Sample impact = at(1.0);
        double horizontal = (impact.horizontalFraction() - before.horizontalFraction()) * DISTANCE;
        double angle = Math.toDegrees(Math.atan((before.altitude() - impact.altitude()) / horizontal));
        assertEquals(BallisticTrajectory.IMPACT_ANGLE_DEGREES, angle, 1.0E-4);
    }

    @Test
    void continuousWithoutJumps() {
        BallisticTrajectory.Sample prev = at(0.0);
        for (int i = 1; i <= 4000; i++) {
            BallisticTrajectory.Sample cur = at(i / 4000.0);
            assertTrue(cur.horizontalFraction() >= prev.horizontalFraction() - 1.0E-9,
                    "горизонталь не должна откатываться назад");
            assertTrue(Math.abs(cur.altitude() - prev.altitude()) < 5.0,
                    "скачок высоты на шаге " + i);
            prev = cur;
        }
    }

    @Test
    void shortRouteRemainsContinuousAndKeepsImpactAngle() {
        double lastTick = 1.0 / 40.0;
        BallisticTrajectory.Sample before = BallisticTrajectory.sample(
                1.0 - lastTick, 120.0, 70.0, 64.0, 800.0);
        BallisticTrajectory.Sample impact = BallisticTrajectory.sample(
                1.0, 120.0, 70.0, 64.0, 800.0);
        double horizontal = 64.0 * (impact.horizontalFraction() - before.horizontalFraction());
        double angle = Math.toDegrees(Math.atan(
                (before.altitude() - impact.altitude()) / horizontal));
        assertTrue(Double.isFinite(before.altitude()));
        assertEquals(70.0, impact.altitude(), 1.0E-9);
        assertEquals(BallisticTrajectory.IMPACT_ANGLE_DEGREES, angle, 1.0E-4);
    }
}
```

- [ ] **Step 2: Убедиться, что тесты не компилируются/падают**

Run: `./gradlew test --tests 'ru.liko.pjmbasemod.common.missile.BallisticTrajectoryTest' 2>&1 | tail -20`
Expected: FAIL — `Sample`/`sample` не существуют (ошибка компиляции).

- [ ] **Step 3: Переписать BallisticTrajectory**

Полное содержимое `src/main/java/ru/liko/pjmbasemod/common/missile/BallisticTrajectory.java`:

```java
package ru.liko.pjmbasemod.common.missile;

/**
 * Квазибаллистический профиль стратегической ракеты.
 *
 * <p>Вертикальный старт (квадратичная Безье с вертикальной начальной касательной),
 * параболический набор до апогея с нулевым наклоном в вершине, затем терминальное
 * пикирование с плавным доворотом до 80°. Все участки C1-непрерывны. Позиция —
 * чистая функция нормализованного времени: класс без MC-зависимостей.</p>
 */
public final class BallisticTrajectory {

    public static final double IMPACT_ANGLE_DEGREES = 80.0;

    /** Точка профиля: доля горизонтального пути [0..1] и высота. */
    public record Sample(double horizontalFraction, double altitude) {}

    private static final double IMPACT_SLOPE = Math.tan(Math.toRadians(IMPACT_ANGLE_DEGREES));
    /** Доля пикирования, за которую наклон плавно возрастает от 0 до 80°. */
    private static final double DIVE_TRANSITION_FRACTION = 0.25;
    /** Начало прямого участка в запасном профиле для слишком коротких маршрутов. */
    private static final double FALLBACK_LINEAR_START = 0.75;
    /** Доля горизонтали, занимаемая вертикальным стартом. */
    private static final double BOOST_HORIZONTAL_FRACTION = 0.06;
    /** Доля времени на буст — из непрерывности горизонтальной скорости: 2β/(1+β). */
    private static final double BOOST_TIME_FRACTION =
            2.0 * BOOST_HORIZONTAL_FRACTION / (1.0 + BOOST_HORIZONTAL_FRACTION);
    /** Какую долю подъёма к апогею проходит буст. */
    private static final double BOOST_CLIMB_FRACTION = 0.25;
    private static final double EPSILON = 1.0E-6;

    private BallisticTrajectory() {}

    /**
     * Точка траектории в нормализованный момент полёта {@code progress}.
     *
     * @param horizontalDistance горизонтальная длина всего маршрута
     * @param apexHeight         высота апогея над более высокой из начальной и конечной точек
     */
    public static Sample sample(double progress, double startY, double targetY,
                                double horizontalDistance, double apexHeight) {
        double t = clamp01(progress);
        double distance = Math.max(0.0, horizontalDistance);
        if (distance < EPSILON) {
            return new Sample(t, lerp(t, startY, targetY));
        }

        double apexY = Math.max(startY, targetY) + Math.max(0.0, apexHeight);
        double drop = apexY - targetY;
        // Средний наклон пикирования учитывает площадь переходного участка (доворот 0→80°).
        double transitionFactor = 1.0 - DIVE_TRANSITION_FRACTION / 2.0;
        double diveDistance = drop / (IMPACT_SLOPE * transitionFactor);
        double diveStart = 1.0 - diveDistance / distance;

        if (diveStart <= BOOST_HORIZONTAL_FRACTION + 0.05) {
            // Маршрут слишком короткий для буста и параболы — запасной сплайн.
            return new Sample(t, hermiteFallback(t, startY, targetY, distance));
        }

        double u = horizontalAt(t);
        return new Sample(u, altitudeAt(u, startY, apexY, diveStart, diveDistance));
    }

    /** Тайм-варп: буст разгоняет горизонтальную скорость от нуля, дальше она постоянна. */
    private static double horizontalAt(double t) {
        if (t < BOOST_TIME_FRACTION) {
            double w = t / BOOST_TIME_FRACTION;
            return BOOST_HORIZONTAL_FRACTION * w * w;
        }
        return BOOST_HORIZONTAL_FRACTION + (1.0 - BOOST_HORIZONTAL_FRACTION)
                * (t - BOOST_TIME_FRACTION) / (1.0 - BOOST_TIME_FRACTION);
    }

    private static double altitudeAt(double u, double startY, double apexY,
                                     double diveStart, double diveDistance) {
        double burnoutY = startY + BOOST_CLIMB_FRACTION * (apexY - startY);
        if (u < BOOST_HORIZONTAL_FRACTION) {
            // Безье: старт строго вверх, конечная касательная равна начальному наклону параболы.
            double w = Math.sqrt(u / BOOST_HORIZONTAL_FRACTION);
            double slopeU = 2.0 * (apexY - burnoutY) / (diveStart - BOOST_HORIZONTAL_FRACTION);
            double controlY = Math.max(startY + 1.0,
                    burnoutY - slopeU * BOOST_HORIZONTAL_FRACTION);
            double inv = 1.0 - w;
            return inv * inv * startY + 2.0 * w * inv * controlY + w * w * burnoutY;
        }
        if (u < diveStart) {
            // Парабола: замедляющийся набор к апогею, наклон в вершине 0 (стык с пикированием).
            double w = (u - BOOST_HORIZONTAL_FRACTION) / (diveStart - BOOST_HORIZONTAL_FRACTION);
            double inv = 1.0 - w;
            return apexY - (apexY - burnoutY) * inv * inv;
        }
        double dive = (u - diveStart) / (1.0 - diveStart);
        if (dive < DIVE_TRANSITION_FRACTION) {
            double transitionDrop = 0.5 * IMPACT_SLOPE * diveDistance
                    * dive * dive / DIVE_TRANSITION_FRACTION;
            return apexY - transitionDrop;
        }
        double transitionDrop = 0.5 * IMPACT_SLOPE * diveDistance * DIVE_TRANSITION_FRACTION;
        double linearDrop = IMPACT_SLOPE * diveDistance * (dive - DIVE_TRANSITION_FRACTION);
        return apexY - transitionDrop - linearDrop;
    }

    /**
     * Запасной профиль для слишком короткого маршрута. Сплайн плавно переходит
     * в прямой терминальный участок под 80°. Горизонталь — равномерная (u = t).
     */
    private static double hermiteFallback(double t, double startY, double targetY, double distance) {
        double linearStartY = targetY
                + IMPACT_SLOPE * distance * (1.0 - FALLBACK_LINEAR_START);
        if (t >= FALLBACK_LINEAR_START) {
            return targetY + IMPACT_SLOPE * distance * (1.0 - t);
        }

        double spline = t / FALLBACK_LINEAR_START;
        double spline2 = spline * spline;
        double spline3 = spline2 * spline;
        double startBasis = 2.0 * spline3 - 3.0 * spline2 + 1.0;
        double targetBasis = -2.0 * spline3 + 3.0 * spline2;
        double targetTangentBasis = spline3 - spline2;
        double splineDistance = distance * FALLBACK_LINEAR_START;
        return startBasis * startY
                + targetBasis * linearStartY
                + targetTangentBasis * (-splineDistance * IMPACT_SLOPE);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double lerp(double progress, double start, double end) {
        return start + (end - start) * progress;
    }
}
```

- [ ] **Step 4: Прогнать тесты**

Run: `./gradlew test --tests 'ru.liko.pjmbasemod.common.missile.BallisticTrajectoryTest' 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, 6 tests passed.

- [ ] **Step 5: Перевести ballisticPosition на sample**

В `StrategicMissileEntity` заменить метод `ballisticPosition` целиком:

```java
    private Vec3 ballisticPosition(double t) {
        double horizontalDistance = Math.hypot(targetX - startX, targetZ - startZ);
        BallisticTrajectory.Sample sample = BallisticTrajectory.sample(
                t, startY, targetY, horizontalDistance, ballisticApex);
        double x = Mth.lerp(sample.horizontalFraction(), startX, targetX);
        double z = Mth.lerp(sample.horizontalFraction(), startZ, targetZ);
        return new Vec3(x, sample.altitude(), z);
    }
```

- [ ] **Step 6: Сборка**

Run: `./gradlew compileJava compileClientJava -q; echo EXIT=$?`
Expected: EXIT=0.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/missile/BallisticTrajectory.java \
        src/test/java/ru/liko/pjmbasemod/common/missile/BallisticTrajectoryTest.java \
        src/main/java/ru/liko/pjmbasemod/common/entity/StrategicMissileEntity.java
git commit -m "feat(missile): квазибаллистика — вертикальный старт, парабола, доворот 80°

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Pop-up фазы крылатой ракеты

**Files:**
- Modify: `src/main/java/ru/liko/pjmbasemod/common/missile/MissileDefinition.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/entity/StrategicMissileEntity.java`
- Modify: `docs/MISSILE_STRIKES.md`

**Interfaces:**
- Produces: `MissileDefinition.popupDistance()` / `popupHeight()` (int). JSON-поля `popupDistance` (default 120, 0 = выкл), `popupHeight` (default 60).

Юнит-теста нет: `cruisePosition` требует `ServerLevel` (heightmap). Верификация — компиляция + внутриигровая проверка пользователем.

- [ ] **Step 1: Поля в MissileDefinition**

После строки `int ballisticApex = 160;` добавить:

```java
    /** За сколько блоков до цели крылатая ракета делает горку (0 — без горки). */
    int popupDistance = 120;
    /** Высота горки над целью перед пикированием. */
    int popupHeight = 60;
```

В `normalize()` после строки `ballisticApex = clamp(ballisticApex, 32, 800);`:

```java
        popupDistance = clamp(popupDistance, 0, 600);
        popupHeight = clamp(popupHeight, 8, 200);
```

К аксессорам (рядом с `ballisticApex()`):

```java
    public int popupDistance() { return popupDistance; }
    public int popupHeight() { return popupHeight; }
```

- [ ] **Step 2: Поля в StrategicMissileEntity**

После `private int ballisticApex = 160;` добавить:

```java
    private int popupDistance;
    private int popupHeight = 60;
```

В `configure(...)` после `this.ballisticApex = definition.ballisticApex();`:

```java
        this.popupDistance = definition.popupDistance();
        this.popupHeight = definition.popupHeight();
```

В `readAdditionalSaveData` после строки `ballisticApex = tag.getInt("BallisticApex");`:

```java
        popupDistance = tag.getInt("PopupDistance");
        popupHeight = tag.getInt("PopupHeight");
```

В `addAdditionalSaveData` после `tag.putInt("BallisticApex", ballisticApex);`:

```java
        tag.putInt("PopupDistance", popupDistance);
        tag.putInt("PopupHeight", popupHeight);
```

- [ ] **Step 3: Горка в cruisePosition**

В `cruisePosition` сразу после строки
`double cruiseTarget = Math.min(level.getMaxBuildHeight() - 8.0, lookAheadTerrain(...) + cruiseHeight);`
добавить:

```java
        // Pop-up у цели (профиль ПКР): горка перед терминальным пикированием — существующее
        // сглаживание вертикальной скорости само отработает набор, dive² спикирует из вершины.
        if (popupDistance > 0 && horizontalRemaining < popupDistance) {
            cruiseTarget = Math.max(cruiseTarget,
                    Math.min(level.getMaxBuildHeight() - 8.0, targetY + popupHeight));
        }
```

- [ ] **Step 4: Документация**

В `docs/MISSILE_STRIKES.md` в описание JSON-полей профиля добавить строки (рядом с `terminalDiveDistance`):

```markdown
| `popupDistance` | 120 | За сколько блоков до цели крылатая ракета делает горку (pop-up). `0` — летит без горки, старое поведение. |
| `popupHeight` | 60 | Высота горки над целью: из её вершины ракета круто пикирует в цель. |
```

(если поля описаны списком, а не таблицей — добавить в том же стиле, что соседние).

- [ ] **Step 5: Сборка**

Run: `./gradlew compileJava -q; echo EXIT=$?`
Expected: EXIT=0.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/missile/MissileDefinition.java \
        src/main/java/ru/liko/pjmbasemod/common/entity/StrategicMissileEntity.java \
        docs/MISSILE_STRIKES.md
git commit -m "feat(missile): pop-up фазы крылатой ракеты — горка у цели и крутое пикирование

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: MissileTrackPacket — серверная часть трека

**Files:**
- Create: `src/main/java/ru/liko/pjmbasemod/common/network/packet/MissileTrackPacket.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/network/PjmNetworking.java` (регистрация + `VERSION`)
- Modify: `src/main/java/ru/liko/pjmbasemod/common/network/ClientPacketProxy.java`
- Modify: `src/main/java/ru/liko/pjmbasemod/common/entity/StrategicMissileEntity.java`

**Interfaces:**
- Produces: `MissileTrackPacket(UUID id, String dimension, double x, double z, float yaw, boolean active)`; `ClientPacketProxy.missileTrack(MissileTrackPacket)` (default noop). Task 4 реализует обработчик и отрисовку.

- [ ] **Step 1: Создать пакет**

Полное содержимое `src/main/java/ru/liko/pjmbasemod/common/network/packet/MissileTrackPacket.java`:

```java
package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.UUID;

/**
 * S→C: живой трек летящей ракеты для карты — только команде, запустившей ракету.
 * Сервер шлёт раз в 10 тиков без лимита дистанции; {@code active=false} — ракета
 * детонировала или сбита, стрелку с карты убрать.
 */
public record MissileTrackPacket(UUID id, String dimension, double x, double z,
                                 float yaw, boolean active)
        implements CustomPacketPayload {

    public static final Type<MissileTrackPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "missile_track"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissileTrackPacket> STREAM_CODEC =
            StreamCodec.of(MissileTrackPacket::write, MissileTrackPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, MissileTrackPacket packet) {
        buf.writeUUID(packet.id);
        buf.writeUtf(packet.dimension, 256);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.z);
        buf.writeFloat(packet.yaw);
        buf.writeBoolean(packet.active);
    }

    private static MissileTrackPacket read(RegistryFriendlyByteBuf buf) {
        return new MissileTrackPacket(buf.readUUID(), buf.readUtf(256),
                buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 2: Зарегистрировать + бамп VERSION**

В `PjmNetworking.java`: строку `public static final String VERSION = "56";` заменить на `"57"`.
После строки регистрации `MissileAlertPacket` (строка ~109) добавить:

```java
        r.playToClient(MissileTrackPacket.TYPE, MissileTrackPacket.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CLIENT.missileTrack(p)));
```

Импорт `ru.liko.pjmbasemod.common.network.packet.MissileTrackPacket` — рядом с остальными missile-пакетами.

- [ ] **Step 3: Метод в ClientPacketProxy**

После `default void missileAlert(MissileAlertPacket payload) {}` добавить (+ импорт `MissileTrackPacket`):

```java
    /** Живой трек ракеты для карты (только своей команде). */
    default void missileTrack(MissileTrackPacket payload) {}
```

- [ ] **Step 4: Отправка из StrategicMissileEntity**

В `tick()` после строки `if (tickCount % 2 == 0) sendAudioSync(serverLevel, true);` добавить:

```java
        if (tickCount % 10 == 0) sendTrack(serverLevel, true);
```

Рядом с `sendAudioSync` добавить метод:

```java
    /** Трек для карты — только своей команде, мимо entity-трекинга и без лимита дистанции. */
    private void sendTrack(ServerLevel level, boolean active) {
        if (teamId.isBlank()) return;
        PjmNetworking.sendToTeam(level.getServer(), teamId, new MissileTrackPacket(
                getUUID(), level.dimension().location().toString(),
                getX(), getZ(), getYRot(), active));
    }
```

В `remove(RemovalReason reason)` рядом с `sendAudioSync(serverLevel, false);` добавить `sendTrack(serverLevel, false);`.
Импорт `ru.liko.pjmbasemod.common.network.packet.MissileTrackPacket`.

- [ ] **Step 5: Сборка**

Run: `./gradlew compileJava -q; echo EXIT=$?`
Expected: EXIT=0.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ru/liko/pjmbasemod/common/network/packet/MissileTrackPacket.java \
        src/main/java/ru/liko/pjmbasemod/common/network/PjmNetworking.java \
        src/main/java/ru/liko/pjmbasemod/common/network/ClientPacketProxy.java \
        src/main/java/ru/liko/pjmbasemod/common/entity/StrategicMissileEntity.java
git commit -m "feat(missile): MissileTrackPacket — серверный трек ракеты для команды пуска (VERSION 57)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Клиент — стейт трека и стрелка на карте

**Files:**
- Modify: `src/client/java/ru/liko/pjmbasemod/client/missile/ClientMissileState.java`
- Modify: `src/client/java/ru/liko/pjmbasemod/client/network/ClientPacketHandlersImpl.java`
- Modify: `src/client/java/ru/liko/pjmbasemod/client/worldmap/overlay/MapOverlays.java`
- Modify: `docs/MISSILE_STRIKES.md`

**Interfaces:**
- Consumes: `MissileTrackPacket`, `ClientPacketProxy.missileTrack` (Task 3).
- Produces: `ClientMissileState.Track(String dimension, double x, double z, float yaw, long timeMs)`; `updateTrack(MissileTrackPacket)`; `Collection<Track> tracks()`.

- [ ] **Step 1: Трек-стейт в ClientMissileState**

Добавить импорты `java.util.Collection`, `java.util.HashMap`, `java.util.Map`, `java.util.UUID`, `ru.liko.pjmbasemod.common.network.packet.MissileTrackPacket`. После записи `StrikeAlert` и полей добавить:

```java
    /** Позиция и курс живой ракеты своей команды. */
    public record Track(String dimension, double x, double z, float yaw, long timeMs) {}

    /** Сколько трек живёт без апдейтов (потеря пакета/дисконнект) — потом стрелка гаснет. */
    private static final long TRACK_TTL_MS = 3_000L;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    public static void updateTrack(MissileTrackPacket packet) {
        if (packet == null) return;
        if (!packet.active()) {
            TRACKS.remove(packet.id());
            return;
        }
        TRACKS.put(packet.id(), new Track(packet.dimension(), packet.x(), packet.z(),
                packet.yaw(), Util.getMillis()));
    }

    /** Живые треки (протухшие отсеиваются на месте). */
    public static Collection<Track> tracks() {
        long now = Util.getMillis();
        TRACKS.values().removeIf(track -> now - track.timeMs() > TRACK_TTL_MS);
        return TRACKS.values();
    }
```

- [ ] **Step 2: Обработчик в ClientPacketHandlersImpl**

После метода `missileAlert(...)` (строка ~284) добавить в том же стиле:

```java
    @Override
    public void missileTrack(ru.liko.pjmbasemod.common.network.packet.MissileTrackPacket payload) {
        ru.liko.pjmbasemod.client.missile.ClientMissileState.updateTrack(payload);
    }
```

- [ ] **Step 3: Стрелка в MapOverlays**

В `render(...)` перед `drawMissileAlerts(...)` добавить вызов `drawMissileTracks(gg, camX, camZ, scale, width, height, dim);`. Новый метод (рядом с `drawMissileAlerts`):

```java
    private static final int TRACK_COLOR = 0xE6A640;

    /**
     * Стрелки живых ракет своей команды: позиция и курс, апдейт с сервера раз в 10 тиков.
     * Приватность гарантирует сервер — чужим командам трек просто не приходит.
     */
    private static void drawMissileTracks(GuiGraphics gg, double camX, double camZ,
                                          double scale, int width, int height, String dim) {
        for (ClientMissileState.Track track : ClientMissileState.tracks()) {
            if (!track.dimension().equals(dim)) continue;
            double sx = MapRenderer.worldToScreenX(track.x(), camX, scale, width);
            double sy = MapRenderer.worldToScreenY(track.z(), camZ, scale, height);
            if (sx < -24 || sx > width + 24 || sy < -24 || sy > height + 24) continue;

            // Курс из yaw сущности: направление полёта в мировых (x, z) = (-sin, cos).
            double a = Math.toRadians(track.yaw());
            double dx = -Math.sin(a), dz = Math.cos(a);
            double px = -dz, pz = dx;
            double[] xs = {sx + dx * 10, sx - dx * 6 + px * 5, sx - dx * 3, sx - dx * 6 - px * 5};
            double[] ys = {sy + dz * 10, sy - dz * 6 + pz * 5, sy - dz * 3, sy - dz * 6 - pz * 5};
            MapRenderer.fillPolygon(gg, xs, ys, 4, 0xCC000000 | TRACK_COLOR, width, height);
            for (int i = 0; i < 4; i++) {
                MapRenderer.line(gg, xs[i], ys[i], xs[(i + 1) % 4], ys[(i + 1) % 4],
                        1.5f, 0xFF000000 | TRACK_COLOR);
            }
        }
    }
```

- [ ] **Step 4: Документация**

В `docs/MISSILE_STRIKES.md` в раздел про карту добавить:

```markdown
Пока ракета летит, команда, запустившая её, видит на карте янтарную стрелку — живую
позицию и курс ракеты (апдейт раз в полсекунды, работает в любой точке мира).
Вражеские команды стрелку не видят — у них только отложенная зона предупреждения.
```

- [ ] **Step 5: Сборка**

Run: `./gradlew compileJava compileClientJava -q; echo EXIT=$?`
Expected: EXIT=0.

- [ ] **Step 6: Финальная проверка всего**

Run: `./gradlew test --tests 'ru.liko.pjmbasemod.common.missile.BallisticTrajectoryTest' compileJava compileClientJava 2>&1 | tail -3`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/client/java/ru/liko/pjmbasemod/client/missile/ClientMissileState.java \
        src/client/java/ru/liko/pjmbasemod/client/network/ClientPacketHandlersImpl.java \
        src/client/java/ru/liko/pjmbasemod/client/worldmap/overlay/MapOverlays.java \
        docs/MISSILE_STRIKES.md
git commit -m "feat(missile): стрелка живого трека ракеты на карте для команды пуска

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
