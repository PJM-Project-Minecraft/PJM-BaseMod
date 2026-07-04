package ru.liko.pjmbasemod.client.moderation;

import ru.liko.pjmbasemod.common.moderation.ModerationSnapshot;

import javax.annotation.Nullable;

/** Клиентское зеркало последнего снимка модерации (на случай прихода sync без открытого экрана). */
public final class ClientModerationState {

    @Nullable
    private static ModerationSnapshot snapshot;

    private ClientModerationState() {}

    public static void update(ModerationSnapshot next) {
        snapshot = next;
    }

    @Nullable
    public static ModerationSnapshot snapshot() {
        return snapshot;
    }
}
