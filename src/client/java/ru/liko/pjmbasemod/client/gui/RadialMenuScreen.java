package ru.liko.pjmbasemod.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Matrix4f;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.chat.ClientChatModeState;
import ru.liko.pjmbasemod.client.customization.ClientSkinState;
import ru.liko.pjmbasemod.client.input.ModKeyBindings;
import ru.liko.pjmbasemod.client.faction.ClientFactionCommanderState;
import ru.liko.pjmbasemod.client.faction.FactionRankIcons;
import ru.liko.pjmbasemod.client.role.ClientRoleState;
import ru.liko.pjmbasemod.common.chat.ChatMode;
import ru.liko.pjmbasemod.common.customization.CustomizationType;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.ChangeChatModePacket;
import ru.liko.pjmbasemod.common.network.packet.RequestFactionManagementPacket;
import ru.liko.pjmbasemod.common.network.packet.RequestTargetRoleAccessPacket;
import ru.liko.pjmbasemod.common.network.packet.SelectCustomizationPacket;
import ru.liko.pjmbasemod.common.network.packet.SelectRolePacket;
import ru.liko.pjmbasemod.common.role.CombatRole;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class RadialMenuScreen extends Screen {

    private static final int INNER_RADIUS = 30;
    private static final int OUTER_RADIUS = 90;
    private static final ResourceLocation CLASS_ICON = ResourceLocation.fromNamespaceAndPath(
            Pjmbasemod.MODID, "textures/icon/class.png");

    private List<RadialAction> actions = new ArrayList<>();
    private int hoveredIndex = -1;
    private int lastHoveredIndex = -1;

    private float openProgress = 0f;
    private float hoveredProgress = 0f;
    private boolean closing = false;

    // Для FPS-независимых анимаций: время предыдущего кадра.
    private long lastFrameNanos;

    private final Player targetPlayer;
    private Page page = Page.MAIN;
    @Nullable
    private UUID roleTargetId;
    @Nullable
    private String roleTargetName;

    public RadialMenuScreen(Player targetPlayer) {
        super(Component.empty());
        this.targetPlayer = targetPlayer;
    }

    @Override
    protected void init() {
        super.init();
        resolveRoleTarget();
        if (page == Page.MAIN) {
            buildMainActions();
        }
    }

    private void buildMainActions() {
        page = Page.MAIN;
        hoveredIndex = -1;
        lastHoveredIndex = -1;
        hoveredProgress = 0f;
        actions = new ArrayList<>();

        actions.add(new RadialAction(
                Component.translatable("gui.pjmbasemod.radial.toggle_chat"),
                0xFF66FF66,
                new ItemStack(Items.WRITABLE_BOOK),
                null,
                null,
                null,
                true,
                (SubmenuAction) p -> openChatSubmenu()));

        actions.add(new RadialAction(
                Component.translatable("gui.pjmbasemod.radial.role"),
                0xFF70B7D8,
                ItemStack.EMPTY,
                CLASS_ICON,
                null,
                null,
                true,
                (SubmenuAction) p -> openRoleSubmenu()));

        actions.add(new RadialAction(
                Component.translatable("gui.pjmbasemod.radial.character"),
                0xFFB59CD8,
                new ItemStack(Items.ARMOR_STAND),
                null,
                null,
                null,
                true,
                (SubmenuAction) p -> openCharacterSubmenu()));

        // Командир фракции: открыть экран управления фракцией (роли/замы/приказ).
        if (ClientFactionCommanderState.state().active()) {
            actions.add(new RadialAction(
                    Component.translatable("gui.pjmbasemod.radial.faction_management"),
                    0xFFF0B43A,
                    ItemStack.EMPTY,
                    FactionRankIcons.COMMANDER,
                    null,
                    null,
                    true,
                    p -> PjmNetworking.sendToServer(RequestFactionManagementPacket.INSTANCE)));
        }

        if (actions.isEmpty()) {
            actions.add(new RadialAction(
                    Component.translatable("gui.pjmbasemod.radial.no_actions"),
                    0xFF888888,
                    ItemStack.EMPTY,
                    null,
                    null,
                    null,
                    false,
                    p -> {}));
        }
    }

    private void openChatSubmenu() {
        page = Page.CHAT;
        hoveredIndex = -1;
        lastHoveredIndex = -1;
        hoveredProgress = 0f;
        actions = new ArrayList<>();

        for (ChatMode mode : ChatMode.values()) {
            actions.add(new RadialAction(
                    Component.translatable("gui.pjmbasemod.radial.chat." + mode.getId()),
                    chatModeColor(mode),
                    new ItemStack(Items.WRITABLE_BOOK),
                    null,
                    mode,
                    null,
                    true,
                    p -> {
                        ClientChatModeState.setMode(mode);
                        PjmNetworking.sendToServer(ChangeChatModePacket.setMode(mode));
                    }));
        }

        actions.add(new RadialAction(
                Component.translatable("gui.pjmbasemod.radial.back"),
                0xFFAAAAAA,
                new ItemStack(Items.ARROW),
                null,
                null,
                null,
                true,
                (SubmenuAction) p -> buildMainActions()));
    }

    private void openRoleSubmenu() {
        page = Page.ROLE;
        hoveredIndex = -1;
        lastHoveredIndex = -1;
        hoveredProgress = 0f;
        actions = new ArrayList<>();

        boolean commanderMode = ClientRoleState.canAssignRoles() && roleTargetId != null;
        UUID selfId = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getUUID()
                : null;

        for (CombatRole role : CombatRole.values()) {
            boolean enabled;
            UUID assignTarget;
            if (commanderMode) {
                // Платные роли, которыми цель не владеет, гасим (сервер всё равно отклонит назначение).
                enabled = ClientRoleState.isTargetAssignable(roleTargetId, role);
                assignTarget = roleTargetId;
            } else {
                enabled = ClientRoleState.isSelfAssignable(role);
                assignTarget = selfId;
            }
            final UUID target = assignTarget;
            actions.add(new RadialAction(
                    Component.translatable(role.translationKey()),
                    0xFF000000 | role.color(),
                    iconFor(role),
                    null,
                    null,
                    role.id(),
                    enabled,
                    p -> {
                        if (target != null) {
                            PjmNetworking.sendToServer(new SelectRolePacket(target, role.id()));
                        }
                    }));
        }

        if (commanderMode) {
            actions.add(new RadialAction(
                    Component.translatable("gui.pjmbasemod.radial.role_clear"),
                    0xFFB05050,
                    new ItemStack(Items.BARRIER),
                    null,
                    null,
                    "",
                    true,
                    p -> PjmNetworking.sendToServer(new SelectRolePacket(roleTargetId, ""))));
        }

        actions.add(new RadialAction(
                Component.translatable("gui.pjmbasemod.radial.back"),
                0xFFAAAAAA,
                new ItemStack(Items.ARROW),
                null,
                null,
                null,
                true,
                (SubmenuAction) p -> buildMainActions()));
    }

    private void openCharacterSubmenu() {
        page = Page.CHARACTER;
        hoveredIndex = -1;
        lastHoveredIndex = -1;
        hoveredProgress = 0f;
        actions = new ArrayList<>();

        actions.add(new RadialAction(
                Component.translatable("gui.pjmbasemod.radial.customization"),
                0xFF8FB3C8,
                new ItemStack(Items.LEATHER_CHESTPLATE),
                null,
                null,
                null,
                true,
                (SubmenuAction) p -> openSkinSubmenu()));

        actions.add(new RadialAction(
                Component.translatable("gui.pjmbasemod.radial.back"),
                0xFFAAAAAA,
                new ItemStack(Items.ARROW),
                null,
                null,
                null,
                true,
                (SubmenuAction) p -> buildMainActions()));
    }

    private void openSkinSubmenu() {
        page = Page.SKIN;
        hoveredIndex = -1;
        lastHoveredIndex = -1;
        hoveredProgress = 0f;
        actions = new ArrayList<>();

        List<String> skins = ClientSkinState.allowed();
        if (skins.isEmpty()) {
            actions.add(new RadialAction(
                    Component.translatable("gui.pjmbasemod.radial.skin_none"),
                    0xFF888888,
                    ItemStack.EMPTY,
                    null,
                    null,
                    null,
                    false,
                    p -> {}));
        } else {
            for (String skin : skins) {
                actions.add(new RadialAction(
                        skinLabel(skin),
                        0xFF8FB3C8,
                        new ItemStack(Items.LEATHER_CHESTPLATE),
                        null,
                        null,
                        skin,
                        true,
                        p -> PjmNetworking.sendToServer(
                                new SelectCustomizationPacket(CustomizationType.PLAYER_SKIN, skin))));
            }
        }

        actions.add(new RadialAction(
                Component.translatable("gui.pjmbasemod.radial.back"),
                0xFFAAAAAA,
                new ItemStack(Items.ARROW),
                null,
                null,
                null,
                true,
                (SubmenuAction) p -> openCharacterSubmenu()));
    }

    /** Подпись скина: ключ локализации {@code skin.pjmbasemod.<id>} с фолбэком на «красивый» id. */
    private Component skinLabel(String skinId) {
        return Component.translatableWithFallback("skin.pjmbasemod." + skinId, prettifySkin(skinId));
    }

    private String prettifySkin(String skinId) {
        String base = skinId.startsWith("skin_") ? skinId.substring("skin_".length()) : skinId;
        base = base.replace('_', ' ').trim();
        if (base.isEmpty()) return skinId;
        StringBuilder sb = new StringBuilder(base.length());
        boolean capitalize = true;
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (c == ' ') {
                capitalize = true;
                sb.append(c);
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private int chatModeColor(ChatMode mode) {
        return switch (mode) {
            case LOCAL -> 0xFF55FFFF;
            case GLOBAL -> 0xFFFFAA00;
            case TEAM -> 0xFFFFFF55;
        };
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        if (button == 0 && hoveredIndex >= 0 && hoveredIndex < actions.size()) {
            executeAction(hoveredIndex);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (isMovementKey(key)) {
            KeyMapping.set(key, true);
            return false;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (isMovementKey(key)) {
            KeyMapping.set(key, false);
            return false;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private boolean isMovementKey(InputConstants.Key key) {
        var opts = Minecraft.getInstance().options;
        return opts.keyUp.isActiveAndMatches(key)
                || opts.keyDown.isActiveAndMatches(key)
                || opts.keyLeft.isActiveAndMatches(key)
                || opts.keyRight.isActiveAndMatches(key)
                || opts.keyJump.isActiveAndMatches(key)
                || opts.keySprint.isActiveAndMatches(key)
                || opts.keyShift.isActiveAndMatches(key);
    }

    @Override
    public void tick() {
        super.tick();
        if (closing) return;
        boolean isKeyDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(),
                ModKeyBindings.RADIAL_MENU.getKey().getValue());
        if (!isKeyDown) {
            closing = true;
        }
    }

    private void executeAction(int index) {
        RadialAction action = actions.get(index);
        if (!action.enabled() || action.onSelect() == null) return;

        PjmUiSounds.playPress();

        boolean isSubmenuNavigation = action.onSelect() instanceof SubmenuAction;
        if (isSubmenuNavigation) {
            action.onSelect().accept(Minecraft.getInstance().player);
        } else {
            this.onClose();
            action.onSelect().accept(Minecraft.getInstance().player);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // FPS-независимые коэффициенты сглаживания: зависят от реального времени кадра.
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 1f / 60f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        dt = Mth.clamp(dt, 0f, 0.1f);
        float closeStep = 1f - (float) Math.exp(-dt * 9.7f);   // ≈ 0.15 при 60 FPS
        float openStep  = 1f - (float) Math.exp(-dt * 30.6f);  // ≈ 0.4  при 60 FPS (partialTick≈0.5..1)
        float hoverStep = 1f - (float) Math.exp(-dt * 40.5f);  // ≈ 0.5  при 60 FPS

        if (closing) {
            openProgress = lerp(openProgress, 0f, closeStep);
            if (openProgress < 0.03f) {
                this.onClose();
                return;
            }
        } else {
            openProgress = lerp(openProgress, 1.0f, openStep);
        }

        float scale = 0.8f + (openProgress * 0.2f);
        float alpha = openProgress;

        int centerX = width / 2;
        int centerY = height / 2;
        int count = actions.size();

        if (closing) {
            hoveredIndex = -1;
        } else {
            double dx = mouseX - centerX;
            double dy = mouseY - centerY;
            double dist = Math.sqrt(dx * dx + dy * dy);
            hoveredIndex = -1;

            if (dist >= INNER_RADIUS && dist <= OUTER_RADIUS + 40 && count > 0) {
                double angle = Math.atan2(dy, dx);
                if (angle < 0) angle += Math.PI * 2;

                double segmentAngle = (Math.PI * 2) / count;
                double offsetAngle = angle + Math.PI / 2;
                if (offsetAngle >= Math.PI * 2) offsetAngle -= Math.PI * 2;

                hoveredIndex = (int) (offsetAngle / segmentAngle) % count;
            }
        }

        if (hoveredIndex != lastHoveredIndex) {
            hoveredProgress = 0f;
            if (!closing && hoveredIndex >= 0) {
                PjmUiSounds.playShared();
            }
            lastHoveredIndex = hoveredIndex;
        }
        hoveredProgress = lerp(hoveredProgress, 1.0f, hoverStep);

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.pose().translate(-centerX, -centerY, 0);

        int alphaI = (int) (alpha * 255);

        for (int i = 0; i < count; i++) {
            boolean isHovered = i == hoveredIndex;
            float hoverFactor = isHovered ? hoveredProgress : 0f;

            RadialAction action = actions.get(i);

            double segmentAngle = (Math.PI * 2) / count;
            double startAngle = -Math.PI / 2 + i * segmentAngle;
            double endAngle = startAngle + segmentAngle;

            int currentInnerR = INNER_RADIUS + (int) (hoverFactor * 5);
            int currentOuterR = OUTER_RADIUS + (int) (hoverFactor * 10);

            int bgAlpha = isHovered ? (int) (alpha * 240) : (int) (alpha * 180);
            if (!action.enabled()) bgAlpha = (int) (alpha * 120);

            int bgColor = (isHovered ? 0x222222 : 0x111111) | (bgAlpha << 24);
            int borderRgb = action.enabled()
                    ? (isHovered ? action.color() & 0x00FFFFFF : activeColor(action))
                    : 0x555555;
            int borderColor = borderRgb | (alphaI << 24);

            double gap = count > 1 ? 0.02 : 0.0;
            drawSmoothArc(graphics, centerX, centerY, currentInnerR, currentOuterR,
                    startAngle, endAngle, bgColor, borderColor, gap);

            double midAngle = (startAngle + endAngle) / 2.0;
            int contentRadius = (currentInnerR + currentOuterR) / 2;
            int itemX = centerX + (int) (Math.cos(midAngle) * contentRadius);
            int itemY = centerY + (int) (Math.sin(midAngle) * contentRadius);

            graphics.pose().pushPose();
            graphics.pose().translate(itemX - 8, itemY - 18, 100);
            if (isHovered) {
                float itemScale = 1.0f + hoverFactor * 0.2f;
                graphics.pose().scale(itemScale, itemScale, 1f);
            }
            if (action.textureIcon() != null) {
                graphics.blit(action.textureIcon(), 0, 0, 0, 0, 16, 16, 16, 16);
            } else if (!action.icon().isEmpty()) {
                graphics.renderItem(action.icon(), 0, 0);
            }
            graphics.pose().popPose();

            Component label = action.label();
            int textWidth = font.width(label);
            int baseTextColor = action.enabled() ? (isHovered ? 0xFFFFFF : 0xAAAAAA) : 0x666666;
            if (isActive(action) && action.enabled()) {
                baseTextColor = action.color() & 0x00FFFFFF;
            }
            int textColor = baseTextColor | (alphaI << 24);
            int textYOffset = action.icon().isEmpty() && action.textureIcon() == null ? -4 : 4;

            graphics.drawString(font, label, itemX - textWidth / 2, itemY + textYOffset,
                    textColor, true);
        }

        drawCircle(graphics, centerX, centerY, INNER_RADIUS - 5, 0x111111 | ((int) (alpha * 220) << 24));
        drawCircleOutline(graphics, centerX, centerY, INNER_RADIUS - 5, 0x444444 | (alphaI << 24), 2.0f);

        Component centerText = centerText();
        int ctW = font.width(centerText);
        graphics.drawString(font, centerText, centerX - ctW / 2, centerY - 4,
                0xCCCCCC | (alphaI << 24), false);

        graphics.pose().popPose();
    }

    private int activeColor(RadialAction action) {
        return isActive(action) ? action.color() & 0x00FFFFFF : 0x444444;
    }

    private boolean isActive(RadialAction action) {
        if (page == Page.SKIN && action.roleId() != null) {
            return action.roleId().equals(ClientSkinState.current());
        }
        if (action.chatMode() != null) {
            return action.chatMode() == ClientChatModeState.getMode();
        }
        if (action.roleId() != null) {
            return action.roleId().equals(ClientRoleState.currentRole());
        }
        return false;
    }

    private Component centerText() {
        if (page == Page.CHAT) {
            return Component.translatable("gui.pjmbasemod.radial.chat_mode");
        }
        if (page == Page.CHARACTER) {
            return Component.translatable("gui.pjmbasemod.radial.character");
        }
        if (page == Page.SKIN) {
            return Component.translatable("gui.pjmbasemod.radial.customization");
        }
        if (page == Page.ROLE) {
            if (ClientRoleState.canAssignRoles()) {
                return roleTargetName == null
                        ? Component.translatable("gui.pjmbasemod.radial.role_no_target")
                        : Component.translatable("gui.pjmbasemod.radial.role_target", roleTargetName);
            }
            CombatRole role = ClientRoleState.currentRoleEnum();
            return role == null
                    ? Component.translatable("gui.pjmbasemod.radial.role_none")
                    : Component.translatable("gui.pjmbasemod.radial.current_role", Component.translatable(role.translationKey()));
        }
        return Component.translatable("gui.pjmbasemod.radial.title");
    }

    private void resolveRoleTarget() {
        roleTargetId = null;
        roleTargetName = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof EntityHitResult hit) {
            Entity entity = hit.getEntity();
            if (entity instanceof Player player && player != targetPlayer) {
                roleTargetId = player.getUUID();
                roleTargetName = player.getName().getString();
            }
        }
        // Командир: запросить у сервера, какие роли можно назначить этой цели (для гашения недоступных).
        ClientRoleState.clearTargetAccess();
        if (roleTargetId != null && ClientRoleState.canAssignRoles()) {
            PjmNetworking.sendToServer(new RequestTargetRoleAccessPacket(roleTargetId));
        }
    }

    /** Вызывается из сетевого обработчика при получении владения цели — перестраивает меню ролей, если оно открыто. */
    public void onTargetRoleAccessUpdated() {
        if (page == Page.ROLE) {
            openRoleSubmenu();
        }
    }

    private ItemStack iconFor(CombatRole role) {
        return switch (role) {
            case ASSAULT -> new ItemStack(Items.IRON_SWORD);
            case MACHINE_GUNNER -> new ItemStack(Items.CROSSBOW);
            case SNIPER -> new ItemStack(Items.SPYGLASS);
            case UAV_OPERATOR -> new ItemStack(Items.COMPASS);
            case SSO -> new ItemStack(Items.NETHERITE_SWORD);
            case MARKSMAN -> new ItemStack(Items.BOW);
            case EW_SPECIALIST -> new ItemStack(Items.REDSTONE);
            case CREW -> new ItemStack(Items.MINECART);
        };
    }

    private float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }

    private void drawSmoothArc(GuiGraphics graphics, int cx, int cy, int innerR, int outerR,
            double startAngle, double endAngle, int fillColor, int borderColor, double gap) {
        float fA = (float) (fillColor >> 24 & 0xFF) / 255.0f;
        float fR = (float) (fillColor >> 16 & 0xFF) / 255.0f;
        float fG = (float) (fillColor >> 8 & 0xFF) / 255.0f;
        float fB = (float) (fillColor & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP,
                DefaultVertexFormat.POSITION_COLOR);

        int steps = Math.max((int) (Math.toDegrees(Math.abs(endAngle - startAngle))), 64);

        double effectiveStart = startAngle + gap;
        double effectiveEnd = endAngle - gap;

        if (effectiveEnd <= effectiveStart) {
            effectiveStart = startAngle;
            effectiveEnd = endAngle;
        }

        double angleStep = (effectiveEnd - effectiveStart) / steps;

        for (int i = 0; i <= steps; i++) {
            double angle = effectiveStart + i * angleStep;
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);

            buffer.addVertex(matrix, cx + cosA * outerR, cy + sinA * outerR, 0).setColor(fR, fG, fB, fA);
            buffer.addVertex(matrix, cx + cosA * innerR, cy + sinA * innerR, 0).setColor(fR, fG, fB, fA);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        float bA = (float) (borderColor >> 24 & 0xFF) / 255.0f;
        float bR = (float) (borderColor >> 16 & 0xFF) / 255.0f;
        float bG = (float) (borderColor >> 8 & 0xFF) / 255.0f;
        float bB = (float) (borderColor & 0xFF) / 255.0f;
        float borderThickness = 1.5f;

        BufferBuilder border = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP,
                DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= steps; i++) {
            double angle = effectiveStart + i * angleStep;
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);
            border.addVertex(matrix, cx + cosA * (outerR + borderThickness),
                    cy + sinA * (outerR + borderThickness), 0).setColor(bR, bG, bB, bA);
            border.addVertex(matrix, cx + cosA * outerR, cy + sinA * outerR, 0).setColor(bR, bG, bB, bA);
        }
        BufferUploader.drawWithShader(border.buildOrThrow());

        border = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP,
                DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= steps; i++) {
            double angle = effectiveStart + i * angleStep;
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);
            border.addVertex(matrix, cx + cosA * innerR, cy + sinA * innerR, 0).setColor(bR, bG, bB, bA);
            border.addVertex(matrix, cx + cosA * (innerR - borderThickness),
                    cy + sinA * (innerR - borderThickness), 0).setColor(bR, bG, bB, bA);
        }

        BufferUploader.drawWithShader(border.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void drawCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        drawSmoothArc(graphics, cx, cy, 0, radius, 0, Math.PI * 2, color, 0, 0.0);
    }

    private void drawCircleOutline(GuiGraphics graphics, int cx, int cy, int radius, int color, float width) {
        float a = (float) (color >> 24 & 0xFF) / 255.0f;
        float r = (float) (color >> 16 & 0xFF) / 255.0f;
        float g = (float) (color >> 8 & 0xFF) / 255.0f;
        float b = (float) (color & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP,
                DefaultVertexFormat.POSITION_COLOR);

        int steps = 180;
        for (int i = 0; i <= steps; i++) {
            double angle = (Math.PI * 2) * i / (double) steps;
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);
            buffer.addVertex(matrix, cx + cosA * (radius + width / 2), cy + sinA * (radius + width / 2), 0)
                    .setColor(r, g, b, a);
            buffer.addVertex(matrix, cx + cosA * (radius - width / 2), cy + sinA * (radius - width / 2), 0)
                    .setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private enum Page {
        MAIN,
        CHAT,
        ROLE,
        CHARACTER,
        SKIN
    }

    @FunctionalInterface
    private interface SubmenuAction extends Consumer<Player> {
    }

    private record RadialAction(Component label, int color, ItemStack icon,
                                @Nullable ResourceLocation textureIcon,
                                @Nullable ChatMode chatMode,
                                @Nullable String roleId,
                                boolean enabled,
                                Consumer<Player> onSelect) {
    }
}
