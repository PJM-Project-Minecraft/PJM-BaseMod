package ru.liko.pjmbasemod.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ru.liko.pjmbasemod.common.init.PjmBlocks;

import javax.annotation.Nullable;

/**
 * Сборка и разбор мультиблока «Ремка»: ядро в центре основания плюс невидимый наполнитель
 * вокруг. Наполнитель намеренно не хранит ссылку на ядро — держать 74 лишних BlockEntity
 * ради редкого события «сломали станцию» дороже, чем разово просканировать окрестность.
 */
public final class RemkaStructure {

    /**
     * Защёлка от рекурсии. {@code onRemove} вызывается при любой смене блока, независимо от
     * флагов обновления, поэтому снос структуры иначе запускал бы сам себя на каждом снятом
     * блоке. ThreadLocal, а не обычный {@code static}: в одиночной игре клиентский и серверный
     * уровни живут в разных потоках и затирали бы общий флаг друг другу.
     */
    private static final ThreadLocal<Boolean> DESTROYING = ThreadLocal.withInitial(() -> false);

    private RemkaStructure() {}

    /** Свободно ли место под всю структуру с ядром в {@code core}. */
    public static boolean canPlace(LevelAccessor level, BlockPos core) {
        for (BlockPos pos : positions(core)) {
            if (level.isOutsideBuildHeight(pos)) return false;
            if (!level.getBlockState(pos).canBeReplaced()) return false;
        }
        return true;
    }

    /** Ставит структуру: ядро в {@code core}, наполнитель вокруг. */
    public static void place(LevelAccessor level, BlockPos core, Direction facing) {
        BlockState filler = PjmBlocks.REMKA.get().defaultBlockState()
                .setValue(RemkaBlock.CORE, false)
                .setValue(RemkaBlock.FACING, facing);
        BlockState coreState = filler.setValue(RemkaBlock.CORE, true);

        for (BlockPos pos : positions(core)) {
            level.setBlock(pos, pos.equals(core) ? coreState : filler, Block.UPDATE_ALL);
        }
    }

    /**
     * Сносит структуру, которой принадлежит {@code from}. Ядро ищем в окрестности: у наполнителя
     * нет обратной ссылки. Блоки снимаем без {@code UPDATE_NEIGHBORS}, чтобы каскад
     * {@code onRemove} не запускал снос повторно на каждом из них.
     */
    public static void destroy(Level level, BlockPos from) {
        if (DESTROYING.get()) return;
        DESTROYING.set(true);
        try {
            BlockPos core = findCore(level, from);
            if (core == null) {
                // Ядро не нашлось (снесли командой, порушился чанк) — убираем хотя бы этот блок.
                if (isPart(level, from)) level.setBlock(from, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                return;
            }
            for (BlockPos pos : positions(core)) {
                if (isPart(level, pos)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        } finally {
            DESTROYING.set(false);
        }
    }

    /**
     * Ищет ядро структуры, накрывающей {@code from}. Радиус поиска — размер структуры:
     * дальше ядро своей же станции оказаться не может.
     */
    @Nullable
    public static BlockPos findCore(Level level, BlockPos from) {
        for (int dx = -RemkaBlock.RADIUS; dx <= RemkaBlock.RADIUS; dx++) {
            for (int dz = -RemkaBlock.RADIUS; dz <= RemkaBlock.RADIUS; dz++) {
                for (int dy = -(RemkaBlock.HEIGHT - 1); dy <= 0; dy++) {
                    BlockPos pos = from.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(PjmBlocks.REMKA.get()) && state.getValue(RemkaBlock.CORE)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isPart(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(PjmBlocks.REMKA.get());
    }

    /** Все позиции структуры с ядром в центре основания. */
    public static Iterable<BlockPos> positions(BlockPos core) {
        return BlockPos.betweenClosed(
                core.offset(-RemkaBlock.RADIUS, 0, -RemkaBlock.RADIUS),
                core.offset(RemkaBlock.RADIUS, RemkaBlock.HEIGHT - 1, RemkaBlock.RADIUS));
    }
}
