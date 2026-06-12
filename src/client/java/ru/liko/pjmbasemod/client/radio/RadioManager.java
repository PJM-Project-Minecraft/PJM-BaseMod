package ru.liko.pjmbasemod.client.radio;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import ru.liko.pjmbasemod.client.input.ModKeyBindings;
import ru.liko.pjmbasemod.common.init.PjmSounds;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.RadioSwitchPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RadioManager {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final RadioManager INSTANCE = new RadioManager();
    private static final long STALE_SPEAKER_TIMEOUT_MS = 3000;
    private static final int SVC_CACHE_INTERVAL = 5;

    public static RadioManager get() { return INSTANCE; }

    private boolean transmitting;
    private final Map<UUID, Long> activeTeammates = new ConcurrentHashMap<>();

    @Nullable
    private RadioStaticSoundInstance staticSound;

    private int svcCacheTick;
    private boolean cachedLocalChat;
    @Nullable
    private String cachedLocalSpeakerName;

    private RadioManager() {}

    public boolean isTransmitting() {
        return transmitting;
    }

    public void startTransmit() {
        if (transmitting) return;
        transmitting = true;
        PjmNetworking.sendToServer(new RadioSwitchPacket(true));
        playSound(true);
    }

    public void stopTransmit() {
        if (!transmitting) return;
        transmitting = false;
        PjmNetworking.sendToServer(new RadioSwitchPacket(false));
        playSound(false);
    }

    public void onTeammateStartRadio(UUID senderId) {
        boolean isNew = !activeTeammates.containsKey(senderId);
        activeTeammates.put(senderId, System.currentTimeMillis());
        if (isNew) {
            playSound(true);
            startStaticIfNeeded();
        }
    }

    public void onTeammateStopRadio(UUID senderId) {
        if (activeTeammates.remove(senderId) != null) {
            playSound(false);
            stopStaticIfEmpty();
        }
    }

    public boolean isLocalChatActive() {
        return cachedLocalChat;
    }

    @Nullable
    public String getLocalSpeakerName() {
        return cachedLocalSpeakerName;
    }

    @Nullable
    public String getRadioSpeakerName() {
        cleanupStale();
        if (activeTeammates.isEmpty()) return null;

        UUID recentSpeakerId = activeTeammates.entrySet()
                .stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
        return recentSpeakerId == null ? null : resolvePlayerName(recentSpeakerId);
    }

    @Nullable
    private String resolvePlayerName(UUID playerId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            var player = mc.level.getPlayerByUUID(playerId);
            if (player != null) {
                return player.getGameProfile().getName();
            }
        }
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(playerId);
            if (info != null) {
                return info.getProfile().getName();
            }
        }
        return null;
    }

    private boolean checkLocalChatActive() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                cachedLocalSpeakerName = null;
                return false;
            }

            de.maxhenkel.voicechat.voice.client.ClientVoicechat client =
                    de.maxhenkel.voicechat.voice.client.ClientManager.getClient();
            if (client == null) {
                cachedLocalSpeakerName = null;
                return false;
            }

            de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager stateManager =
                    de.maxhenkel.voicechat.voice.client.ClientManager.getPlayerStateManager();
            UUID ownGroupId = stateManager == null ? null : stateManager.getGroupID();

            List<String> localTalkers = new ArrayList<>();

            if (stateManager != null) {
                for (de.maxhenkel.voicechat.voice.common.PlayerState state : stateManager.getPlayerStates(false)) {
                    if (state == null) continue;
                    if (state.isDisconnected()) continue;
                    if (state.isDisabled()) continue;
                    UUID speakerId = state.getUuid();
                    if (speakerId == null) continue;
                    if (ownGroupId != null && ownGroupId.equals(state.getGroup())) continue;
                    if (activeTeammates.containsKey(speakerId)) continue;

                    if (client.getTalkCache().isTalking(speakerId)) {
                        localTalkers.add(state.getName());
                    }
                }
            }

            boolean ownMicTalking = !transmitting
                    && client.getMicThread() != null
                    && client.getMicThread().isTalking();
            if (ownMicTalking) {
                localTalkers.add(0, mc.player.getGameProfile().getName());
            }

            if (localTalkers.isEmpty()) {
                cachedLocalSpeakerName = null;
                return false;
            }

            cachedLocalSpeakerName = localTalkers.get(0);
            return true;
        } catch (Exception | NoClassDefFoundError e) {
            LOGGER.debug("SVC local chat check failed", e);
            cachedLocalSpeakerName = null;
            return false;
        }
    }

    public void tick() {
        boolean currentlyPressed = ModKeyBindings.COMMAND_RADIO.isDown();
        VoiceChatBridge.tick(currentlyPressed);

        if (currentlyPressed && !transmitting) {
            startTransmit();
        } else if (!currentlyPressed && transmitting) {
            stopTransmit();
        }

        if (++svcCacheTick >= SVC_CACHE_INTERVAL) {
            svcCacheTick = 0;
            cachedLocalChat = checkLocalChatActive();
        }
    }

    private void cleanupStale() {
        long now = System.currentTimeMillis();
        boolean hadSpeakers = !activeTeammates.isEmpty();
        activeTeammates.entrySet().removeIf(entry -> now - entry.getValue() > STALE_SPEAKER_TIMEOUT_MS);
        if (hadSpeakers && activeTeammates.isEmpty()) {
            stopStaticLoop();
        }
    }

    private void playSound(boolean start) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                        start ? PjmSounds.RADIO_START.get() : PjmSounds.RADIO_END.get(),
                        1.0f, 0.8f));
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to play radio sound", e);
        }
    }

    private void startStaticIfNeeded() {
        if (staticSound != null && !staticSound.isStopped()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                staticSound = new RadioStaticSoundInstance();
                mc.getSoundManager().play(staticSound);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to start radio static sound", e);
        }
    }

    private void stopStaticIfEmpty() {
        if (!activeTeammates.isEmpty()) return;
        stopStaticLoop();
    }

    private void stopStaticLoop() {
        if (staticSound == null) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(staticSound);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to stop radio static sound", e);
        }
        staticSound.stopSound();
        staticSound = null;
    }

    public void reset() {
        transmitting = false;
        activeTeammates.clear();
        cachedLocalChat = false;
        cachedLocalSpeakerName = null;
        svcCacheTick = 0;
        stopStaticLoop();
        VoiceChatBridge.reset();
        VoiceChatActionBarHud.reset();
    }
}
