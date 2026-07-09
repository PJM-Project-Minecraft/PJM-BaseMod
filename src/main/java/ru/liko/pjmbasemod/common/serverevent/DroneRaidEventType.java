package ru.liko.pjmbasemod.common.serverevent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import ru.liko.pjmbasemod.common.compat.WrbDronesCompat;
import ru.liko.pjmbasemod.common.serverevent.DroneRaidDefinition.RaidPoint;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Тип события «налёт дронов» ({@link DroneRaidEvent}). Требует загруженного мода WRBDrones.
 */
public final class DroneRaidEventType implements ServerEventType {

    public static final DroneRaidEventType INSTANCE = new DroneRaidEventType();

    private DroneRaidEventType() {}

    @Override
    public String typeId() {
        return DroneRaidEvent.TYPE_ID;
    }

    @Override
    public boolean available(MinecraftServer server) {
        return WrbDronesCompat.isLoaded();
    }

    @Override
    public CreateResult create(MinecraftServer server, @Nullable String pointName) {
        if (!WrbDronesCompat.isLoaded()) {
            return CreateResult.error("мод wrbdrones не загружен — налёт дронов недоступен");
        }
        DroneRaidRegistry registry = DroneRaidRegistry.get();
        List<RaidPoint> points = registry.points();
        if (points.isEmpty()) {
            return CreateResult.error("нет настроенных точек налёта (config/pjmbasemod/events/drone_raid.json)");
        }

        RaidPoint point;
        if (pointName != null && !pointName.isBlank()) {
            point = registry.pointByName(pointName);
            if (point == null) {
                return CreateResult.error("точка '" + pointName + "' не найдена");
            }
        } else {
            point = points.get(ThreadLocalRandom.current().nextInt(points.size()));
        }

        return CreateResult.ok(DroneRaidEvent.create(point, registry.settings()));
    }

    @Override
    public DroneRaidEvent load(CompoundTag tag) {
        return DroneRaidEvent.load(tag);
    }
}
