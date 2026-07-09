package ru.liko.pjmbasemod.common.serverevent;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;

/** Персистентность системы событий: таймер автозапуска + снапшот активного события. */
public final class ServerEventSavedData extends SavedData {

    private static final String DATA_NAME = "pjmbasemod_server_events";
    private static final SavedData.Factory<ServerEventSavedData> FACTORY = new SavedData.Factory<>(
            ServerEventSavedData::new,
            ServerEventSavedData::load
    );

    /** Момент следующего автозапуска (epoch-секунды); 0 — не запланирован. */
    private long nextEventAtEpochSeconds;
    /** Снапшот активного события (с полем Type) или null. */
    @Nullable
    private CompoundTag activeEvent;

    public static ServerEventSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static ServerEventSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerEventSavedData data = new ServerEventSavedData();
        data.nextEventAtEpochSeconds = tag.getLong("NextEventAt");
        if (tag.contains("ActiveEvent", Tag.TAG_COMPOUND)) {
            data.activeEvent = tag.getCompound("ActiveEvent");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextEventAt", nextEventAtEpochSeconds);
        if (activeEvent != null) {
            tag.put("ActiveEvent", activeEvent);
        }
        return tag;
    }

    public long nextEventAtEpochSeconds() {
        return nextEventAtEpochSeconds;
    }

    public void setNextEventAtEpochSeconds(long value) {
        nextEventAtEpochSeconds = value;
        setDirty();
    }

    @Nullable
    public CompoundTag activeEvent() {
        return activeEvent;
    }

    public void setActiveEvent(@Nullable CompoundTag tag) {
        activeEvent = tag;
        setDirty();
    }
}
