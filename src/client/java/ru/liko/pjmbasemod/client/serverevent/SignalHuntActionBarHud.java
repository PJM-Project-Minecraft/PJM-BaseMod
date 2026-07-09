package ru.liko.pjmbasemod.client.serverevent;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import ru.liko.pjmbasemod.common.init.PjmItems;
import ru.liko.pjmbasemod.common.network.packet.SignalHuntHudPacket;

/**
 * Клиентский actionbar-HUD Радио-детектора. Раз в тик формирует {@link Gui#setOverlayMessage}
 * из последнего {@link SignalHuntHudPacket} и взгляда игрока: полоса силы сигнала + стрелка
 * направления + прогресс перехвата. Широковещательный actionbar НЕ трогается, если детектор
 * не в руке / нет свежего пакета (не затирает voice-chat HUD и прочее).
 */
@OnlyIn(Dist.CLIENT)
public final class SignalHuntActionBarHud {

    private static final long PACKET_FRESH_MS = 1000L;
    private static final int BAR_SEGMENTS = 8;

    private static boolean shown = false;

    private SignalHuntActionBarHud() {}

    public static void reset() {
        shown = false;
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            clearIfShown(mc);
            return;
        }
        if (!isHoldingDetector(mc)) {
            clearIfShown(mc);
            return;
        }

        ClientSignalHuntState.SignalHuntView view = ClientSignalHuntState.current();
        if (!view.valid()) {
            clearIfShown(mc);
            return;
        }
        SignalHuntHudPacket packet = view.packet();
        long age = System.currentTimeMillis() - view.receivedAt();
        if (!packet.active() || age > PACKET_FRESH_MS) {
            clearIfShown(mc);
            return;
        }

        mc.gui.setOverlayMessage(build(packet, mc), false);
        shown = true;
    }

    private static void clearIfShown(Minecraft mc) {
        if (shown) {
            mc.gui.setOverlayMessage(Component.empty(), false);
            shown = false;
        }
    }

    private static boolean isHoldingDetector(Minecraft mc) {
        return mc.player.getMainHandItem().is(PjmItems.RADIO_DETECTOR.get())
                || mc.player.getOffhandItem().is(PjmItems.RADIO_DETECTOR.get());
    }

    private static Component build(SignalHuntHudPacket p, Minecraft mc) {
        float yaw = mc.player.getViewYRot(0);
        float relative = Mth.wrapDegrees(p.direction() - yaw);

        Component arrow = arrowFor(relative);
        Component bar = signalBar(p.signalStrength());
        MutableComponent line = Component.literal("◈ ").withStyle(ChatFormatting.AQUA);

        line.append(Component.translatable("hud.pjmbasemod.signal_hunt.signal").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" "))
                .append(bar)
                .append(Component.literal(" "))
                .append(arrow);

        if (p.captureReady()) {
            Component hint;
            if (p.captureSeconds() <= 0) {
                hint = Component.translatable("hud.pjmbasemod.signal_hunt.capture_now")
                        .withStyle(ChatFormatting.GREEN);
            } else {
                hint = Component.translatable("hud.pjmbasemod.signal_hunt.capture_progress",
                                captureBar(p.captureProgress(), p.captureSeconds()))
                        .withStyle(ChatFormatting.GREEN);
            }
            line.append(Component.literal("  ")).append(hint);
        }

        line.append(Component.literal("  [").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(p.capturedCount() + "/" + p.beaconCount())
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));

        return line;
    }

    private static Component signalBar(double strength) {
        int filled = (int) Math.round(strength * BAR_SEGMENTS);
        if (filled > BAR_SEGMENTS) filled = BAR_SEGMENTS;
        if (filled < 0) filled = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            sb.append(i < filled ? '▮' : '▯');
        }
        ChatFormatting color = filled >= BAR_SEGMENTS ? ChatFormatting.RED
                : filled >= BAR_SEGMENTS / 2 ? ChatFormatting.YELLOW
                : ChatFormatting.DARK_GREEN;
        return Component.literal(sb.toString()).withStyle(color);
    }

    private static String captureBar(int progress, int total) {
        if (total <= 0) return "";
        int filled = Mth.clamp(progress * BAR_SEGMENTS / total, 0, BAR_SEGMENTS);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            sb.append(i < filled ? '█' : '░');
        }
        return sb.toString();
    }

    private static Component arrowFor(float relativeDegrees) {
        String arrow;
        ChatFormatting color;
        if (relativeDegrees >= -22.5f && relativeDegrees < 22.5f) { arrow = "▲"; color = ChatFormatting.RED; }
        else if (relativeDegrees >= 22.5f && relativeDegrees < 67.5f) { arrow = "◥"; color = ChatFormatting.GOLD; }
        else if (relativeDegrees >= 67.5f && relativeDegrees < 112.5f) { arrow = "▶"; color = ChatFormatting.GOLD; }
        else if (relativeDegrees >= 112.5f && relativeDegrees < 157.5f) { arrow = "◢"; color = ChatFormatting.GOLD; }
        else if (relativeDegrees >= 157.5f || relativeDegrees < -157.5f) { arrow = "▼"; color = ChatFormatting.YELLOW; }
        else if (relativeDegrees >= -157.5f && relativeDegrees < -112.5f) { arrow = "◣"; color = ChatFormatting.GOLD; }
        else if (relativeDegrees >= -112.5f && relativeDegrees < -67.5f) { arrow = "◀"; color = ChatFormatting.GOLD; }
        else { arrow = "◤"; color = ChatFormatting.GOLD; }
        return Component.literal(arrow).withStyle(color);
    }
}
