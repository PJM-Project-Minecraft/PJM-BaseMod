package ru.liko.pjmbasemod.client.chat;

import ru.liko.pjmbasemod.common.chat.ChatMode;

public final class ClientChatModeState {

    private static ChatMode mode = ChatMode.GLOBAL;

    private ClientChatModeState() {}

    public static ChatMode getMode() { return mode; }
    public static void setMode(ChatMode m) { mode = m == null ? ChatMode.GLOBAL : m; }
    public static ChatMode cycle() {
        mode = mode.next();
        return mode;
    }
}
