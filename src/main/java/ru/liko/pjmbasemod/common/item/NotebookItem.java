package ru.liko.pjmbasemod.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import ru.liko.pjmbasemod.common.entity.NotebookEntity;
import ru.liko.pjmbasemod.common.garage.GarageType;
import ru.liko.pjmbasemod.common.init.PjmEntities;

/**
 * Предмет, размещающий терминал-«ноутбук» ({@link NotebookEntity}) на блоке.
 * Тип ({@link GarageType}) определяет, какой гараж открывает терминал (наземка/авиация).
 */
public class NotebookItem extends Item {

    private final GarageType garageType;

    public NotebookItem(Properties properties, GarageType garageType) {
        super(properties);
        this.garageType = garageType == null ? GarageType.GROUND : garageType;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }

        Direction face = context.getClickedFace();
        BlockPos placePos = context.getClickedPos().relative(face);
        Player player = context.getPlayer();

        NotebookEntity entity = PjmEntities.NOTEBOOK.get().create(level);
        if (entity == null) {
            return InteractionResult.FAIL;
        }

        // Вычисляем угол так, чтобы ноутбук "смотрел" на игрока (добавляем 180 градусов),
        // и привязываем к ортогональной сетке блоков (шаг в 90 градусов).
        float yaw = player == null ? 0F : player.getYRot();
        float snappedYaw = Math.round((yaw + 180.0F) / 90.0F) * 90.0F;

        // Убрали +0.2D по Y, чтобы ноутбук не висел в воздухе, а стоял на блоке
        entity.moveTo(placePos.getX() + 0.5D, placePos.getY(), placePos.getZ() + 0.5D, snappedYaw, 0F);
        
        // Явно обновляем старые значения поворота, чтобы при спавне не было "дергания" или игнорирования угла
        entity.setYRot(snappedYaw);
        entity.yRotO = snappedYaw;

        if (player != null) {
            entity.setOwner(player.getUUID());
        }
        entity.setGarageType(garageType);
        level.addFreshEntity(entity);

        ItemStack stack = context.getItemInHand();
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
