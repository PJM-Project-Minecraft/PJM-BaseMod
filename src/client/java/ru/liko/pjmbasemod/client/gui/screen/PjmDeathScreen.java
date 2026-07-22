package ru.liko.pjmbasemod.client.gui.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import java.util.List;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.common.network.packet.RadioSpawnListPacket;

/**
 * Кинематографичный экран смерти, полностью заменяющий ванильный {@code DeathScreen}
 * (перехват в {@code ClientEvents.onScreenOpening}).
 *
 * <p>Резкое затемнение до чёрного, затем проявляются заголовок «ВЫ ПОГИБЛИ», иконка
 * оружия (или вращающаяся 3D-модель техники SBW) и сообщение о смерти. Кнопки
 * появляются только после ролика — как в BF5.</p>
 *
 * <p>Данные (сообщение, оружие/техника) приходят пакетом {@code DeathScreenPacket}
 * и складываются в статику через {@link #trigger}. Пакет отправляется на
 * {@code LivingDeathEvent} — то есть по тому же соединению раньше ванильного
 * {@code ClientboundPlayerCombatKillPacket}, который открывает этот экран,
 * поэтому к моменту {@code init()} данные уже на месте.</p>
 */
public class PjmDeathScreen extends Screen {

    private static final float CONTENT_DELAY = 0.45f; // задержка перед появлением контента, сек
    private static final float CONTENT_FADE = 0.9f;   // плавное проявление контента, сек
    private static final float MAP_DELAY = 2.6f;      // авто-переход на карту развёртывания, сек

    private static final int TITLE_COLOR = 0xFFC03A2E;

    // Статика: заполняется пакетом до открытия экрана, переживает resize (init()).
    private static long triggerTime = 0L;
    private static Component message = Component.empty();
    private static ItemStack iconStack = ItemStack.EMPTY;
    private static String vehicleId = "";
    private static Entity previewEntity;
    private static String cachedVehicleId = "";
    // Радио-рюкзаки команды (мобильные точки возрождения), приходят RadioSpawnListPacket.
    private static List<RadioSpawnListPacket.Entry> radioOptions = List.of();

    public PjmDeathScreen() {
        super(Component.translatable("pjmbasemod.death.title"));
    }

    /** Принимает данные о смерти. Вызывается из {@code ClientPacketHandlersImpl}. */
    public static void trigger(Component deathMessage, ItemStack weaponIcon, String vehicle) {
        message = deathMessage == null ? Component.empty() : deathMessage;
        iconStack = weaponIcon == null ? ItemStack.EMPTY : weaponIcon;
        vehicleId = vehicle == null ? "" : vehicle;
        previewEntity = null;
        cachedVehicleId = "";
        triggerTime = Util.getMillis();
    }

    /** Список радио-рюкзаков команды. Вызывается из {@code ClientPacketHandlersImpl}. */
    public static void setRadioOptions(List<RadioSpawnListPacket.Entry> options) {
        radioOptions = options == null ? List.of() : List.copyOf(options);
    }

    private static float elapsed() {
        return (Util.getMillis() - triggerTime) / 1000f;
    }

    @Override
    protected void init() {
        // Смерть без пакета (сторонний мод / рассинхрон) — ролик стартует с открытия экрана.
        if (triggerTime == 0L) triggerTime = Util.getMillis();
        // Кнопок нет: после кинематики экран сам переходит на карту развёртывания
        // (DeploymentMapScreen), где выбирается точка возрождения — база или рация.
    }

    // ─────────────────────────── рендер ───────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float elapsed = elapsed();
        if (elapsed >= MAP_DELAY && this.minecraft != null && this.minecraft.player != null) {
            // Конструируем ДО setScreen: removed() сбрасывает статики (radioOptions, triggerTime).
            DeploymentMapScreen map = new DeploymentMapScreen(radioOptions, triggerTime);
            this.minecraft.setScreen(map);
            return;
        }
        float contentAlpha = Mth.clamp((elapsed - CONTENT_DELAY) / CONTENT_FADE, 0f, 1f);

        // Резкий кат: сплошной чёрный с первого кадра, мир не просвечивает.
        g.fill(0, 0, this.width, this.height, 0xFF000000);

        if (contentAlpha > 0.02f) {
            drawContent(g, contentAlpha, partialTick);
        }

        // renderBackground() — no-op (фон уже нарисован выше), super.render() рисует виджеты.
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawContent(GuiGraphics g, float contentAlpha, float partialTick) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int textAlpha = (int) (contentAlpha * 255f) << 24;

        // Заголовок «ВЫ ПОГИБЛИ» — крупно, кроваво-красным.
        g.pose().pushPose();
        g.pose().translate(cx, cy - 88, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawCenteredString(this.font, this.title, 0, 0, (TITLE_COLOR & 0x00FFFFFF) | textAlpha);
        g.pose().popPose();

        // Иконка оружия или 3D-модель техники по центру.
        if (!vehicleId.isEmpty()) {
            renderVehicle(g, cx, cy - 6, partialTick);
        } else if (!iconStack.isEmpty() && contentAlpha > 0.35f) {
            g.pose().pushPose();
            g.pose().translate(cx, cy - 22, 0);
            g.pose().scale(4f, 4f, 4f);
            g.renderItem(iconStack, -8, -8);
            g.pose().popPose();
        }

        // Сообщение о смерти.
        g.drawCenteredString(this.font, message, cx, cy + 44, 0x00E8E8E8 | textAlpha);

        // Тонкая акцентная черта под сообщением.
        g.fill(cx - 23, cy + 58, cx + 23, cy + 60,
                PjmGuiUtils.withAlpha(PjmGuiUtils.ACCENT, (int) (contentAlpha * 0x77)));
    }

    private void renderVehicle(GuiGraphics g, int centerX, int centerY, float partialTick) {
        Entity entity = previewEntity();
        if (entity == null || this.minecraft.level == null) return;

        EntityRenderDispatcher dispatcher = this.minecraft.getEntityRenderDispatcher();
        if (dispatcher.getRenderer(entity) == null) return;

        AABB box = entity.getBoundingBox();
        float maxSize = Math.max(0.35f, Math.max(
                (float) Math.max(box.getXsize(), box.getZsize()),
                Math.max(entity.getBbHeight(), (float) box.getYsize())));
        float scale = Mth.clamp(70f / maxSize, 3f, 16f);
        float spin = (Util.getMillis() % 10000L) / 10000f * 360f;

        g.pose().pushPose();
        try {
            g.pose().translate(centerX, centerY, 90f);
            g.pose().scale(scale, scale, -scale);
            g.pose().mulPose(Axis.ZP.rotationDegrees(180f));
            g.pose().mulPose(Axis.XP.rotationDegrees(-25f));
            g.pose().mulPose(Axis.YP.rotationDegrees(spin));

            Lighting.setupForEntityInInventory();
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0, 0, 0, 0f, partialTick,
                    g.pose(), g.bufferSource(), 15728880));
            g.flush();
        } catch (RuntimeException ignored) {
            // Проблемный рендер техники не должен ронять экран смерти.
        } finally {
            dispatcher.setRenderShadow(true);
            g.pose().popPose();
            Lighting.setupFor3DItems();
        }
    }

    private Entity previewEntity() {
        if (previewEntity != null && vehicleId.equals(cachedVehicleId)) return previewEntity;
        cachedVehicleId = vehicleId;
        previewEntity = null;
        ResourceLocation id = ResourceLocation.tryParse(vehicleId);
        if (id == null || this.minecraft.level == null) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) return null;
        Entity e = type.create(this.minecraft.level);
        if (e != null) e.moveTo(0, 0, 0, 35f, 0f);
        previewEntity = e;
        return e;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Фон рисуется в render() до контента — иначе он затёр бы модель и заголовок.
    }

    // ─────────────────────────── ввод ───────────────────────────

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        // Иначе следующая смерть без пакета показала бы оружие и сообщение от прошлой.
        // Не вызывается при resize (там только init()), так что ролик не сбрасывается.
        triggerTime = 0L;
        message = Component.empty();
        iconStack = ItemStack.EMPTY;
        vehicleId = "";
        previewEntity = null;
        cachedVehicleId = "";
    }

}
