package ru.liko.pjmbasemod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import ru.liko.pjmbasemod.common.antigrief.GrassClickThrough;

/**
 * Высокая трава и крупный папоротник (tall_grass/large_fern) не таргетятся выжившим
 * игроком — см. {@link GrassClickThrough}. {@code DoublePlantBlock} сам не объявляет
 * {@code getShape} (наследует полный куб от {@code BlockBehaviour}), поэтому здесь
 * не инжект, а добавление override; цветы (сирень, пион и т.п.) не затрагиваются —
 * фильтр по конкретным блокам.
 */
@Mixin(DoublePlantBlock.class)
public abstract class DoublePlantBlockMixin extends BushBlock {

    protected DoublePlantBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if ((state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN))
                && GrassClickThrough.shouldIgnore(context)) {
            return Shapes.empty();
        }
        return super.getShape(state, level, pos, context);
    }
}
