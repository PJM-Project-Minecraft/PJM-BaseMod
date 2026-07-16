package ru.liko.pjmbasemod.common.report;

import net.minecraft.network.FriendlyByteBuf;

/** Одно сообщение в переписке по обращению. {@code fromAdmin} — от администрации или от игрока. */
public record ReportMessage(boolean fromAdmin, String senderName, String text, long time) {

    public static void write(FriendlyByteBuf buf, ReportMessage m) {
        buf.writeBoolean(m.fromAdmin());
        buf.writeUtf(m.senderName());
        buf.writeUtf(m.text());
        buf.writeLong(m.time());
    }

    public static ReportMessage read(FriendlyByteBuf buf) {
        return new ReportMessage(buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readLong());
    }
}
