package ru.liko.pjmbasemod.common.report;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Снимок списка обращений для админского GUI (с полной перепиской каждого). */
public record ReportSnapshot(List<Entry> reports) {

    /** Строка обращения в списке + его переписка. */
    public record Entry(int id, UUID reporterId, String reporterName, ReportCategory category,
                        long createdAt, boolean open, boolean reporterOnline, List<ReportMessage> messages) {}

    public static void write(FriendlyByteBuf buf, ReportSnapshot snapshot) {
        buf.writeVarInt(snapshot.reports().size());
        for (Entry e : snapshot.reports()) {
            buf.writeVarInt(e.id());
            buf.writeUUID(e.reporterId());
            buf.writeUtf(e.reporterName());
            buf.writeEnum(e.category());
            buf.writeLong(e.createdAt());
            buf.writeBoolean(e.open());
            buf.writeBoolean(e.reporterOnline());
            buf.writeVarInt(e.messages().size());
            for (ReportMessage m : e.messages()) ReportMessage.write(buf, m);
        }
    }

    public static ReportSnapshot read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = buf.readVarInt();
            UUID reporterId = buf.readUUID();
            String reporterName = buf.readUtf();
            ReportCategory category = buf.readEnum(ReportCategory.class);
            long createdAt = buf.readLong();
            boolean open = buf.readBoolean();
            boolean online = buf.readBoolean();
            int mc = buf.readVarInt();
            List<ReportMessage> messages = new ArrayList<>(mc);
            for (int j = 0; j < mc; j++) messages.add(ReportMessage.read(buf));
            list.add(new Entry(id, reporterId, reporterName, category, createdAt, open, online, List.copyOf(messages)));
        }
        return new ReportSnapshot(List.copyOf(list));
    }
}
