package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;

public final class CustomHotbarOverlay {
    
    private static long lastInteractionTime = System.currentTimeMillis();
    private static int lastSelectedSlot = -1;
    
    private static final long FADE_DELAY_MS = 4000;
    
    private static float currentAlpha = 0f;
    private static long lastFrameTime = System.currentTimeMillis();
    
    // Массив для хранения текущего масштаба каждого слота
    private static final float[] slotScales = new float[]{1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f};
    
    public static final LayeredDraw.Layer INSTANCE = (g, deltaTracker) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        long currentTime = System.currentTimeMillis();
        float dt = (currentTime - lastFrameTime) / 1000.0f;
        if (dt > 0.1f) dt = 0.1f; // Ограничиваем dt при лагах
        lastFrameTime = currentTime;
        
        int currentSelected = mc.player.getInventory().selected;
        if (currentSelected != lastSelectedSlot) {
            lastInteractionTime = currentTime;
            lastSelectedSlot = currentSelected;
        }
        
        // Показываем, если нет открытых меню (инвентарь, чат, смерть и т.д.) и F1 не нажат
        boolean shouldShow = (mc.screen == null) && !mc.options.hideGui;
        
        float targetAlpha = 0f;
        if (shouldShow && (currentTime - lastInteractionTime < FADE_DELAY_MS)) {
            targetAlpha = 1.0f;
        }
        
        // Плавная анимация прозрачности (fade in/out) без подергиваний (убрали смещение по Y)
        currentAlpha += (targetAlpha - currentAlpha) * 15.0f * dt;
        currentAlpha = Mth.clamp(currentAlpha, 0.0f, 1.0f);
        
        if (currentAlpha <= 0.01f) return;

        int sw = mc.getWindow().getGuiScaledWidth();

        g.pose().pushPose();
        RenderSystem.enableBlend();
        try {
            int numSlots = 9;
            int slotSize = 32;
            int gap = 2;
            int totalWidth = numSlots * slotSize + (numSlots - 1) * gap;
            
            int startX = (sw - totalWidth) / 2;
            int targetY = 40; 
            
            float alpha = currentAlpha;
            int bgAlpha = (int) (alpha * 255);
            
            for (int i = 0; i < numSlots; i++) {
                boolean isSelected = (i == currentSelected);
                
                // Обновляем масштаб слота для плавной анимации увеличения
                float targetScale = isSelected ? 1.15f : 1.0f;
                slotScales[i] += (targetScale - slotScales[i]) * 15.0f * dt;
                float scale = slotScales[i];
                
                int x = startX + i * (slotSize + gap);
                int y = targetY;
                
                g.pose().pushPose();
                // Сдвигаем центр трансформации в центр слота для корректного увеличения
                float centerX = x + slotSize / 2.0f;
                float centerY = y + slotSize / 2.0f;
                g.pose().translate(centerX, centerY, 0);
                g.pose().scale(scale, scale, 1.0f);
                g.pose().translate(-centerX, -centerY, 0);
                
                // 1. Фон слота (полупрозрачный черный)
                int bgColor = ((int)(bgAlpha * 0.6f) << 24) | 0x111111;
                g.fill(x, y, x + slotSize, y + slotSize, bgColor);
                
                // 2. Рамка (тонкая прозрачно-белая)
                int borderColor = ((int)(bgAlpha * 0.15f) << 24) | 0xFFFFFF;
                g.fill(x, y, x + slotSize, y + 1, borderColor); // Верх
                g.fill(x, y, x + 1, y + slotSize, borderColor); // Лево
                g.fill(x + slotSize - 1, y, x + slotSize, y + slotSize, borderColor); // Право
                
                if (isSelected) {
                    // Amber-акцентная полоса снизу для выбранного слота
                    g.fill(x, y + slotSize - 2, x + slotSize, y + slotSize, (bgAlpha << 24) | (PjmGuiUtils.ACCENT & 0x00FFFFFF));
                } else {
                    g.fill(x, y + slotSize - 1, x + slotSize, y + slotSize, borderColor); // Низ
                }
                
                // 3. Номер слота в верхнем левом углу
                String numStr = String.valueOf(i + 1);
                int numBoxW = mc.font.width(numStr) + 4;
                int numBoxH = 10;
                
                if (isSelected) {
                    // Amber-фон, тёмный текст
                    g.fill(x + 1, y + 1, x + 1 + numBoxW, y + 1 + numBoxH, (bgAlpha << 24) | (PjmGuiUtils.ACCENT & 0x00FFFFFF));
                    g.drawString(mc.font, numStr, x + 3, y + 2, (bgAlpha << 24) | 0x0D0D0D, false);
                } else {
                    // Тёмный фон, приглушённый текст
                    g.fill(x + 1, y + 1, x + 1 + numBoxW, y + 1 + numBoxH, ((int)(bgAlpha * 0.5f) << 24) | 0x0D0D0D);
                    g.drawString(mc.font, numStr, x + 3, y + 2, ((int)(bgAlpha * 0.6f) << 24) | 0x888888, false);
                }
                
                // 4. Отрисовка предмета (по центру)
                ItemStack stack = mc.player.getInventory().items.get(i);
                if (!stack.isEmpty()) {
                    int itemX = x + (slotSize - 16) / 2;
                    int itemY = y + (slotSize - 16) / 2;
                    g.renderItem(stack, itemX, itemY);
                    g.renderItemDecorations(mc.font, stack, itemX, itemY);
                    
                    // Если предмет выбран, отображаем его название снизу
                    if (isSelected) {
                        Component itemName = stack.getHoverName();
                        String nameStr = itemName.getString();
                        
                        // Если имя слишком длинное, обрезаем
                        if (mc.font.width(nameStr) > slotSize * 3) {
                            nameStr = mc.font.plainSubstrByWidth(nameStr, slotSize * 3 - 6) + "...";
                        }
                        
                        int nameW = mc.font.width(nameStr);
                        int boxW = Math.max(slotSize, nameW + 8);
                        int boxX = x + (slotSize - boxW) / 2;
                        int boxY = y + slotSize;
                        int boxH = mc.font.lineHeight + 2;
                        
                        // Тёмная плашка с amber текстом под слотом
                        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, ((int)(bgAlpha * 0.85f) << 24) | 0x0D0D0D);
                        g.drawString(mc.font, nameStr, boxX + (boxW - nameW) / 2, boxY + 2, (bgAlpha << 24) | (PjmGuiUtils.ACCENT & 0x00FFFFFF), false);
                    }
                }
                g.pose().popPose();
            }
            
        } finally {
            RenderSystem.disableBlend();
            g.pose().popPose();
        }
    };
    
    public static void updateInteractionTime() {
        lastInteractionTime = System.currentTimeMillis();
    }
}
