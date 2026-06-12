package ru.liko.pjmbasemod.common.voice;

import com.mojang.logging.LogUtils;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.audio.RadioAudioProcessor;
import ru.liko.pjmbasemod.common.frontline.FrontlineTeams;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.RadioEventPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ForgeVoicechatPlugin
public class PjmVoiceChatPlugin implements VoicechatPlugin {

    public static final String PLUGIN_ID = Pjmbasemod.MODID;
    public static final String RADIO_CATEGORY_ID = "pjm_radio";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static PjmVoiceChatPlugin INSTANCE;

    @Nullable
    private VoicechatServerApi serverApi;
    @Nullable
    private VolumeCategory radioCategory;

    private final Map<UUID, OpusDecoder> opusDecoders = new ConcurrentHashMap<>();
    private final Map<UUID, OpusEncoder> opusEncoders = new ConcurrentHashMap<>();
    private final Set<UUID> broadcastingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, RadioAudioProcessor> audioProcessors = new ConcurrentHashMap<>();

    public PjmVoiceChatPlugin() {
        INSTANCE = this;
    }

    @Nullable
    public static PjmVoiceChatPlugin get() {
        return INSTANCE;
    }

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.radioCategory = api.volumeCategoryBuilder()
                .setId(RADIO_CATEGORY_ID)
                .setName("Radio")
                .setDescription("Volume of the command radio channel")
                .build();
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::onPlayerDisconnected);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        this.serverApi = event.getVoicechat();
        if (radioCategory != null && serverApi != null) {
            serverApi.registerVolumeCategory(radioCategory);
            LOGGER.info("[PJM] Registered radio volume category");
        }
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        if (serverApi == null) return;

        VoicechatConnection senderConnection = event.getSenderConnection();
        if (senderConnection == null) return;

        var playerWrapper = senderConnection.getPlayer();
        if (playerWrapper == null) return;

        ServerPlayer sender = (ServerPlayer) playerWrapper.getPlayer();
        if (sender == null) return;
        UUID senderId = sender.getUUID();

        if (!broadcastingPlayers.contains(senderId)) return;

        String team = resolveConfiguredTeam(sender);
        if (team == null) {
            broadcastingPlayers.remove(senderId);
            return;
        }

        List<ServerPlayer> teammates = getTeammates(sender, team);
        if (teammates.isEmpty()) {
            event.cancel();
            return;
        }

        byte[] processedOpus = processAudio(senderId, event.getPacket().getOpusEncodedData());
        UUID radioChannelId = new UUID(senderId.getMostSignificantBits(), ~senderId.getLeastSignificantBits());

        StaticSoundPacket staticPacket = event.getPacket().staticSoundPacketBuilder()
                .channelId(radioChannelId)
                .opusEncodedData(processedOpus)
                .category(RADIO_CATEGORY_ID)
                .build();

        for (ServerPlayer teammate : teammates) {
            VoicechatConnection receiverConnection = serverApi.getConnectionOf(teammate.getUUID());
            if (receiverConnection != null) {
                try {
                    serverApi.sendStaticSoundPacketTo(receiverConnection, staticPacket);
                } catch (Exception e) {
                    LOGGER.debug("Failed to send radio packet to {}: {}", teammate.getName().getString(), e.getMessage());
                }
            }
        }
        event.cancel();
    }

    private byte[] processAudio(UUID senderUuid, byte[] opusData) {
        if (serverApi == null) return opusData;
        try {
            OpusDecoder decoder = opusDecoders.computeIfAbsent(senderUuid, k -> {
                try {
                    return serverApi.createDecoder();
                } catch (Exception e) {
                    return null;
                }
            });
            OpusEncoder encoder = opusEncoders.computeIfAbsent(senderUuid, k -> {
                try {
                    return serverApi.createEncoder();
                } catch (Exception e) {
                    return null;
                }
            });

            if (decoder == null || encoder == null) return opusData;

            short[] pcm = decoder.decode(opusData);
            if (pcm == null) return opusData;

            RadioAudioProcessor processor = audioProcessors.computeIfAbsent(senderUuid, k -> new RadioAudioProcessor());
            short[] processed = processor.process(pcm);
            byte[] encoded = encoder.encode(processed);
            return encoded != null ? encoded : opusData;
        } catch (Exception e) {
            LOGGER.debug("Audio processing error for {}: {}", senderUuid, e.getMessage());
            return opusData;
        }
    }

    private void onPlayerDisconnected(PlayerDisconnectedEvent event) {
        UUID uuid = event.getPlayerUuid();
        broadcastingPlayers.remove(uuid);
        releaseCodecs(uuid);
        RadioAudioProcessor proc = audioProcessors.remove(uuid);
        if (proc != null) proc.reset();
    }

    public void onPlayerStartRadio(ServerPlayer player) {
        if (!canUseTeamRadio(player)) return;

        if (broadcastingPlayers.add(player.getUUID())) {
            notifyTeammates(player, true);
        }
    }

    public void onPlayerStopRadio(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (broadcastingPlayers.remove(uuid)) {
            releaseCodecs(uuid);
            RadioAudioProcessor proc = audioProcessors.remove(uuid);
            if (proc != null) proc.reset();
            notifyTeammates(player, false);
        }
    }

    public void onServerStop() {
        broadcastingPlayers.clear();
        audioProcessors.clear();

        opusDecoders.forEach((uuid, decoder) -> {
            try {
                decoder.close();
            } catch (Exception ignored) {
            }
        });
        opusDecoders.clear();

        opusEncoders.forEach((uuid, encoder) -> {
            try {
                encoder.close();
            } catch (Exception ignored) {
            }
        });
        opusEncoders.clear();
        serverApi = null;
    }

    private void releaseCodecs(UUID uuid) {
        OpusDecoder d = opusDecoders.remove(uuid);
        if (d != null) {
            try {
                d.close();
            } catch (Exception ignored) {
            }
        }
        OpusEncoder e = opusEncoders.remove(uuid);
        if (e != null) {
            try {
                e.close();
            } catch (Exception ignored) {
            }
        }
    }

    public boolean canUseTeamRadio(ServerPlayer player) {
        return resolveConfiguredTeam(player) != null;
    }

    private void notifyTeammates(ServerPlayer sender, boolean isStart) {
        String team = resolveConfiguredTeam(sender);
        if (team == null) return;

        RadioEventPacket packet = new RadioEventPacket(sender.getUUID(), isStart);
        for (ServerPlayer teammate : getTeammates(sender, team)) {
            PjmNetworking.sendToPlayer(teammate, packet);
        }
    }

    private List<ServerPlayer> getTeammates(ServerPlayer sender, String team) {
        List<ServerPlayer> result = new ArrayList<>();
        if (sender.getServer() == null) return result;

        for (ServerPlayer player : sender.getServer().getPlayerList().getPlayers()) {
            if (player.getUUID().equals(sender.getUUID())) continue;
            String otherTeam = resolveConfiguredTeam(player);
            if (team.equalsIgnoreCase(otherTeam)) {
                result.add(player);
            }
        }
        return result;
    }

    @Nullable
    private String resolveConfiguredTeam(ServerPlayer player) {
        return FrontlineTeams.resolvePlayerTeamId(player);
    }
}
