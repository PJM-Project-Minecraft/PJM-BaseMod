package ru.liko.pjmbasemod.common.report;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Снимок одной переписки для клиента игрока. {@code id < 0} → активного обращения нет
 * (клиент показывает форму нового обращения с выбором категории).
 */
public record ReportThread(int id, ReportCategory category, boolean open, List<ReportMessage> messages) {

    /** Маркер «нет активного обращения». */
    public static final ReportThread NONE = new ReportThread(-1, ReportCategory.OTHER, false, List.of());

    public boolean exists() {
        return id >= 0;
    }

    public static void write(FriendlyByteBuf buf, ReportThread t) {
        buf.writeVarInt(t.id());
        buf.writeEnum(t.category());
        buf.writeBoolean(t.open());
        buf.writeVarInt(t.messages().size());
        for (ReportMessage m : t.messages()) ReportMessage.write(buf, m);
    }

    public static ReportThread read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        ReportCategory category = buf.readEnum(ReportCategory.class);
        boolean open = buf.readBoolean();
        int count = buf.readVarInt();
        List<ReportMessage> messages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) messages.add(ReportMessage.read(buf));
        return new ReportThread(id, category, open, List.copyOf(messages));
    }
}
