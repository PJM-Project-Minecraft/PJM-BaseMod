package ru.liko.pjmbasemod.common.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import ru.liko.pjmbasemod.common.blockentity.RemkaBlockEntity;
import ru.liko.pjmbasemod.common.init.PjmBlockEntities;
import ru.liko.pjmbasemod.common.init.PjmItems;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Блок ремонтной станции «Ремка». Станция — мультиблок {@value #FOOTPRINT}x{@value #HEIGHT}x{@value #FOOTPRINT}
 * из блоков одного типа: один «ядро» ({@link #CORE}) с {@link RemkaBlockEntity} и логикой ремонта,
 * остальные — невидимый наполнитель, который и даёт настоящую блочную коллизию по сетке.
 *
 * <p>Модель целиком рисует BER ядра, поэтому все блоки структуры имеют
 * {@link RenderShape#INVISIBLE} — иначе поверх модели торчал бы куб.</p>
 */
public class RemkaBlock extends BaseEntityBlock {

    public static final MapCodec<RemkaBlock> CODEC = simpleCodec(RemkaBlock::new);

    /** Ядро структуры: несёт BlockEntity, логику ремонта и рендер модели. */
    public static final BooleanProperty CORE = BooleanProperty.create("core");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Сторона основания структуры в блоках (нечётная — ядро стоит в центре). */
    public static final int FOOTPRINT = 5;
    /** Высота структуры в блоках. */
    public static final int HEIGHT = 3;

    /** Полурадиус основания: смещение от ядра до края. */
    public static final int RADIUS = FOOTPRINT / 2;

    public RemkaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(CORE, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CORE, FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Всю станцию рисует BER ядра — сами блоки не рендерим.
        return RenderShape.INVISIBLE;
    }

    /**
     * Структура не затеняет свет. По умолчанию это решает {@code getShape()}, а он у нас полный
     * куб ради коллизии — станция затемняла бы сама себя, и BER рисовал бы модель в потёмках
     * (свет он берёт в позиции ядра, то есть внутри структуры).
     */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(CORE) ? new RemkaBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Тикает только ядро и только на сервере: ремонт — серверная логика.
        if (level.isClientSide() || !state.getValue(CORE)) return null;
        return createTickerHelper(type, PjmBlockEntities.REMKA.get(), RemkaBlockEntity::serverTick);
    }

    /**
     * Ломаем любой блок структуры — исчезает вся станция. Иначе от неё оставались бы
     * невидимые куски коллизии, в которые игроки утыкались бы посреди пустоты.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            RemkaStructure.destroy(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Предмет возвращается только за ядро — иначе одна станция дала бы 75 предметов. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return state.getValue(CORE)
                ? Collections.singletonList(new ItemStack(PjmItems.REMKA.get()))
                : Collections.emptyList();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(PjmItems.REMKA.get());
    }
}
