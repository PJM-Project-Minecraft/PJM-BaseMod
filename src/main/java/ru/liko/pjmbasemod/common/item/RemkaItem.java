package ru.liko.pjmbasemod.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import ru.liko.pjmbasemod.common.block.RemkaBlock;
import ru.liko.pjmbasemod.common.block.RemkaStructure;
import ru.liko.pjmbasemod.common.init.PjmSounds;

/**
 * Предмет, собирающий ремонтную станцию «Ремка» — мультиблок с ядром в центре основания.
 */
public class RemkaItem extends Item {

    public RemkaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }

        Direction face = context.getClickedFace();
        BlockPos core = context.getClickedPos().relative(face);
        Player player = context.getPlayer();

        if (!RemkaStructure.canPlace(level, core)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.pjmbasemod.remka.no_space",
                        RemkaBlock.FOOTPRINT, RemkaBlock.HEIGHT, RemkaBlock.FOOTPRINT), true);
            }
            return InteractionResult.FAIL;
        }

        // Станция «смотрит» на игрока: направление взгляда, развёрнутое на 180°.
        Direction facing = player == null ? Direction.NORTH : player.getDirection().getOpposite();
        RemkaStructure.place(level, core, facing);

        level.playSound(null, core, PjmSounds.REMKA_DEPLOY.get(), SoundSource.BLOCKS, 1.0F, 1.0F);

        ItemStack stack = context.getItemInHand();
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
