package ru.liko.pjmbasemod.common.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.liko.pjmbasemod.common.serverevent.ServerEventManager;
import ru.liko.pjmbasemod.common.serverevent.ServerEvent;
import ru.liko.pjmbasemod.common.serverevent.SignalHuntEvent;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * Радио-детектор (GeckoLib-предмет). При удержании в руке во время активного события
 * «радиоразведка» actionbar показывает направление и силу сигнала к ближайшему маяку
 * (см. {@code SignalHuntService.onPlayerTick}). ПКМ в радиусе захвата маяка запускает
 * канал перехвата — удержание позиции даёт XP нашедшему.
 */
public class RadioDetectorItem extends Item implements GeoItem {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.radio_detector.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RadioDetectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), true);
        }
        // ПКМ в воздух — запуск канала перехвата маяка, если активна радиоразведка.
        SignalHuntEvent event = currentSignalHunt();
        if (event != null && event.startCapture(serverPlayer)) {
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Nullable
    private static SignalHuntEvent currentSignalHunt() {
        ServerEvent active = ServerEventManager.activeEvent();
        return active instanceof SignalHuntEvent hunt ? hunt : null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
