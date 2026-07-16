package ru.liko.pjmbasemod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.pjmbasemod.common.antigrief.GrassClickThrough;

/**
 * Короткая трава и папоротник (short_grass/fern) не таргетятся выжившим игроком —
 * см. {@link GrassClickThrough}.
 */
@Mixin(TallGrassBlock.class)
public abstract class TallGrassBlockMixin {

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void pjm_clickThrough(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (GrassClickThrough.shouldIgnore(context)) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
