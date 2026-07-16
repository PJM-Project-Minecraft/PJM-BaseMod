package ru.liko.pjmbasemod.common.antigrief;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import ru.liko.pjmbasemod.Config;

/**
 * «Прозрачная для кликов» трава: у short_grass/fern/tall_grass/large_fern убирается
 * outline-хитбокс для выживших игроков — рейкаст прицела проходит сквозь траву.
 * Игрок не может ни сломать её, ни задеть кликом: ПКМ достаёт предмет/сущность,
 * лежащие в траве, а не сам куст.
 *
 * <p>Хитбокс сохраняется в креативе (админам нужно редактировать траву) и когда игрок
 * держит ножницы (легитимный сбор). Тумблер — {@code general.grassClickThrough}.
 * Применяется миксинами {@code TallGrassBlockMixin} / {@code DoublePlantBlockMixin}.</p>
 */
public final class GrassClickThrough {

    private GrassClickThrough() {}

    /** true — данный рейкаст должен пройти сквозь траву (пустой outline-хитбокс). */
    public static boolean shouldIgnore(CollisionContext context) {
        if (!Config.isGrassClickThroughEnabled()) return false;
        return context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player
                && !player.isCreative()
                && !context.isHoldingItem(Items.SHEARS);
    }
}
