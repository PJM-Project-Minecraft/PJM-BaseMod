package ru.liko.pjmbasemod.client.faction;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * Иконки командного состава фракции, рисуемые рядом с именем командира/зама во всех UI.
 * Текстуры 32×32; blit с textureWidth==width нормализует UV в 0..1 и рисует их целиком в любой размер.
 */
public final class FactionRankIcons {

    /** Погоны — командир фракции (КМД). */
    public static final ResourceLocation COMMANDER =
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/rangs/colonel.png");

    /** Три звезды — заместитель командира (ЗАМ). */
    public static final ResourceLocation DEPUTY =
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/rangs/general.png");

    private FactionRankIcons() {
    }

    /** Рисует иконку целиком в квадрат size×size (текстура любого размера благодаря UV 0..1). */
    public static void draw(GuiGraphics graphics, ResourceLocation icon, int x, int y, int size) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.blit(icon, x, y, 0, 0, size, size, size, size);
    }
}
