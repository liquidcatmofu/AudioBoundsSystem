package io.github.liquidcatmofu.abs.client.audio;

import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.client.sound.ABSDynamicSoundStore;
import io.github.liquidcatmofu.abs.client.sound.ABSSpeakerSoundInstance;
import io.github.liquidcatmofu.abs.server.ABSHttpServer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public final class SpeakerAudioManager {
    public static final SpeakerAudioManager INSTANCE = new SpeakerAudioManager();

    private final Map<BlockPos, ABSSpeakerSoundInstance> activeSources = new HashMap<>();

    private SpeakerAudioManager() {}

    public void play(BlockPos pos, UUID token) {
        String url = "http://localhost:" + ABSHttpServer.DEFAULT_PORT + "/audio/" + token;

        CompletableFuture
                .supplyAsync(() -> fetchAudio(url))
                .thenAccept(bytes -> Minecraft.getInstance().execute(() -> {
                    if (bytes == null) {
                        return;
                    }

                    stop(pos);

                    ResourceLocation dynamicLoc = new ResourceLocation("abs", "sounds/dynamic/" + token + ".ogg");
                    ABSDynamicSoundStore.put(dynamicLoc, bytes);
                    boolean isOgg = bytes.length >= 4 && bytes[0] == 0x4F && bytes[1] == 0x67 && bytes[2] == 0x67 && bytes[3] == 0x53;
                    AudioBoundsSystem.LOGGER.warn("ABS [DIAG] play: stored {} bytes at {} isOgg={}", bytes.length, dynamicLoc, isOgg);

                    ABSSpeakerSoundInstance instance = new ABSSpeakerSoundInstance(dynamicLoc, pos);
                    activeSources.put(pos, instance);
                    AudioBoundsSystem.LOGGER.warn("ABS [DIAG] play: calling SoundManager.play for {}", pos);
                    Minecraft.getInstance().getSoundManager().play(instance);
                    AudioBoundsSystem.LOGGER.warn("ABS [DIAG] play: SoundManager.play returned");
                }));
    }

    public void stop(BlockPos pos) {
        ABSSpeakerSoundInstance instance = activeSources.remove(pos);
        if (instance == null) {
            return;
        }

        Minecraft.getInstance().getSoundManager().stop(instance);
        ABSDynamicSoundStore.remove(instance.getDynamicResourceLoc());
    }

    public void tick(Minecraft client) {
        if (client.level == null) {
            stopAll();
            return;
        }

        ArrayList<BlockPos> stale = new ArrayList<>();
        for (Map.Entry<BlockPos, ABSSpeakerSoundInstance> entry : activeSources.entrySet()) {
            if (!client.getSoundManager().isActive(entry.getValue())) {
                stale.add(entry.getKey());
            }
        }
        stale.forEach(this::stop);
    }

    public void stopAll() {
        new ArrayList<>(activeSources.keySet()).forEach(this::stop);
    }

    private byte[] fetchAudio(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                AudioBoundsSystem.LOGGER.warn("ABS: audio request failed: {} {}", response.statusCode(), url);
                return null;
            }
            return response.body();
        } catch (Exception e) {
            AudioBoundsSystem.LOGGER.error("ABS: failed to fetch speaker audio from {}", url, e);
            return null;
        }
    }
}
