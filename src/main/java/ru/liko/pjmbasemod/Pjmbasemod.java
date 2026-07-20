package ru.liko.pjmbasemod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.liko.pjmbasemod.common.compat.WrbDronesCompat;
import ru.liko.pjmbasemod.common.init.PjmBlockEntities;
import ru.liko.pjmbasemod.common.init.PjmBlocks;
import ru.liko.pjmbasemod.common.init.PjmEntities;
import ru.liko.pjmbasemod.common.init.PjmItems;
import ru.liko.pjmbasemod.common.init.PjmSounds;
import ru.liko.pjmbasemod.common.network.PjmNetworking;

@Mod(Pjmbasemod.MODID)
public final class Pjmbasemod {

    public static final String MODID = "pjmbasemod";
    public static final Logger LOGGER = LoggerFactory.getLogger("PJM-BaseMod");

    public Pjmbasemod(IEventBus modBus, ModContainer container) {
        LOGGER.info("PJM-BaseMod loading…");

        PjmSounds.register(modBus);
        PjmBlocks.register(modBus);
        PjmBlockEntities.register(modBus);
        PjmEntities.register(modBus);
        PjmItems.register(modBus);
        modBus.addListener(PjmNetworking::onRegisterPayloads);
        modBus.addListener(this::onCommonSetup);

        // JSON-конфиг в config/pjmbasemod/config.json (см. Config). Грузим сразу при конструировании мода.
        Config.reload();

        // Слушатель сбития Shahed-136 (XP за событие «налёт дронов»); guard внутри.
        WrbDronesCompat.init();

        // Регистрация типов серверных событий (см. common/serverevent).
        ru.liko.pjmbasemod.common.serverevent.ServerEventManager.registerType(
                ru.liko.pjmbasemod.common.serverevent.DroneRaidEventType.INSTANCE);
        ru.liko.pjmbasemod.common.serverevent.ServerEventManager.registerType(
                ru.liko.pjmbasemod.common.serverevent.SignalHuntEventType.INSTANCE);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("PJM-BaseMod common setup complete.");
    }
}
