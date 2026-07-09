package ru.liko.pjmbasemod.common.serverevent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import ru.liko.pjmbasemod.common.serverevent.SignalHuntDefinition.SignalHuntSettings;
import ru.liko.pjmbasemod.common.serverevent.SignalHuntDefinition.SignalHuntZone;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Тип события «радиоразведка» ({@link SignalHuntEvent}). Не требует внешних модов —
 * маяки невидимые точки, детектор — собственный предмет мода.
 */
public final class SignalHuntEventType implements ServerEventType {

    public static final SignalHuntEventType INSTANCE = new SignalHuntEventType();

    private SignalHuntEventType() {}

    @Override
    public String typeId() {
        return SignalHuntEvent.TYPE_ID;
    }

    @Override
    public boolean available(MinecraftServer server) {
        return true;
    }

    @Override
    public CreateResult create(MinecraftServer server, @Nullable String pointName) {
        SignalHuntRegistry registry = SignalHuntRegistry.get();
        List<SignalHuntZone> zones = registry.zones();
        if (zones.isEmpty()) {
            return CreateResult.error("нет настроенных зон радиоразведки (config/pjmbasemod/events/signal_hunt.json)");
        }

        SignalHuntZone zone;
        if (pointName != null && !pointName.isBlank()) {
            zone = registry.zoneByName(pointName);
            if (zone == null) {
                return CreateResult.error("зона '" + pointName + "' не найдена");
            }
        } else {
            zone = zones.get(ThreadLocalRandom.current().nextInt(zones.size()));
        }

        SignalHuntSettings settings = registry.settings();
        return CreateResult.ok(SignalHuntEvent.create(zone, settings));
    }

    @Override
    public SignalHuntEvent load(CompoundTag tag) {
        return SignalHuntEvent.load(tag);
    }
}
