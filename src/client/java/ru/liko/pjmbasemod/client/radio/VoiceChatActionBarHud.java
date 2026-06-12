package ru.liko.pjmbasemod.client.radio;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class VoiceChatActionBarHud {

    private enum VoiceHudState {
        NONE,
        MOD_MISSING,
        NEEDS_SETUP,
        NOT_CONNECTED,
        VOICE_DISABLED
    }

    private static VoiceHudState previous = VoiceHudState.NONE;

    private VoiceChatActionBarHud() {}

    public static void reset() {
        previous = VoiceHudState.NONE;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            if (previous != VoiceHudState.NONE) {
                minecraft.gui.setOverlayMessage(Component.empty(), false);
                previous = VoiceHudState.NONE;
            }
            return;
        }

        VoiceHudState next = evaluate();
        if (next == VoiceHudState.NONE) {
            if (previous != VoiceHudState.NONE) {
                minecraft.gui.setOverlayMessage(Component.empty(), false);
            }
            previous = VoiceHudState.NONE;
            return;
        }

        minecraft.gui.setOverlayMessage(getMessage(next), false);
        previous = next;
    }

    private static Component getMessage(VoiceHudState state) {
        var gold = ChatFormatting.GOLD;
        return switch (state) {
            case MOD_MISSING -> Component.translatable("overlay.pjmbasemod.voice.action_bar.mod_missing").withStyle(gold);
            case NEEDS_SETUP -> Component.translatable("overlay.pjmbasemod.voice.action_bar.finish_setup").withStyle(gold);
            case NOT_CONNECTED -> Component.translatable("overlay.pjmbasemod.voice.action_bar.not_connected").withStyle(gold);
            case VOICE_DISABLED -> Component.translatable("overlay.pjmbasemod.voice.action_bar.disabled").withStyle(gold);
            default -> Component.empty();
        };
    }

    private static VoiceHudState evaluate() {
        try {
            Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
        } catch (ClassNotFoundException e) {
            return VoiceHudState.MOD_MISSING;
        }

        try {
            return evaluateWhenSvcPresent();
        } catch (LinkageError e) {
            return VoiceHudState.MOD_MISSING;
        } catch (Throwable t) {
            return VoiceHudState.NONE;
        }
    }

    private static VoiceHudState evaluateWhenSvcPresent() {
        if (de.maxhenkel.voicechat.gui.onboarding.OnboardingManager.isOnboarding()) {
            return VoiceHudState.NEEDS_SETUP;
        }

        de.maxhenkel.voicechat.voice.client.ClientVoicechat client =
                de.maxhenkel.voicechat.voice.client.ClientManager.getClient();

        var stateManager = de.maxhenkel.voicechat.voice.client.ClientManager.getPlayerStateManager();
        if (stateManager != null && stateManager.isDisabled()) {
            return VoiceHudState.VOICE_DISABLED;
        }

        if (client == null || client.getConnection() == null) {
            return VoiceHudState.NOT_CONNECTED;
        }
        return VoiceHudState.NONE;
    }
}
