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

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Environment(EnvType.CLIENT)
public final class SpeakerAudioManager {
    public static final SpeakerAudioManager INSTANCE = new SpeakerAudioManager();
    private static final int MAX_AUDIO_BYTES = 64 * 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final Map<BlockPos, ABSSpeakerSoundInstance> activeSources = new HashMap<>();
    private final Map<BlockPos, DownloadOperation> downloads = new HashMap<>();
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2, new AudioDownloadThreadFactory());
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .executor(downloadExecutor)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private SpeakerAudioManager() {}

    public void play(BlockPos pos, UUID token) {
        stopDownload(pos);
        String url = audioUrl(token);
        DownloadOperation operation = new DownloadOperation();
        downloads.put(pos, operation);
        operation.setTask(downloadExecutor.submit(() -> {
            byte[] bytes = null;
            Throwable error = null;
            try {
                bytes = fetchAudio(url);
            } catch (Exception e) {
                error = e;
            }
            byte[] completedBytes = bytes;
            Throwable completedError = error;
            Minecraft.getInstance().execute(() -> {
                    if (downloads.get(pos) != operation) {
                        return;
                    }
                    downloads.remove(pos);
                    if (completedError != null) {
                        if (!operation.isCancelled()) {
                            AudioBoundsSystem.LOGGER.error("ABS: failed to fetch speaker audio from {}", url, completedError);
                        }
                        return;
                    }
                    if (completedBytes == null) {
                        return;
                    }

                    stop(pos);

                    ResourceLocation dynamicLoc = new ResourceLocation("abs", "sounds/dynamic/" + token + ".ogg");
                    ABSDynamicSoundStore.put(dynamicLoc, completedBytes);
                    boolean isOgg = completedBytes.length >= 4 && completedBytes[0] == 0x4F
                            && completedBytes[1] == 0x67 && completedBytes[2] == 0x67 && completedBytes[3] == 0x53;
                    AudioBoundsSystem.LOGGER.debug("ABS [DIAG] play: stored {} bytes at {} isOgg={}", completedBytes.length, dynamicLoc, isOgg);

                    ABSSpeakerSoundInstance instance = new ABSSpeakerSoundInstance(dynamicLoc, pos);
                    activeSources.put(pos, instance);
                    AudioBoundsSystem.LOGGER.debug("ABS [DIAG] play: calling SoundManager.play for {}", pos);
                    Minecraft.getInstance().getSoundManager().play(instance);
                    AudioBoundsSystem.LOGGER.debug("ABS [DIAG] play: SoundManager.play returned");
                });
        }));
    }

    public void stop(BlockPos pos) {
        stopDownload(pos);
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
        for (DownloadOperation operation : new ArrayList<>(downloads.values())) {
            operation.cancel();
        }
        downloads.clear();
        new ArrayList<>(activeSources.keySet()).forEach(this::stop);
    }

    private byte[] fetchAudio(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                AudioBoundsSystem.LOGGER.warn("ABS: audio request failed: {} {}", response.statusCode(), url);
                response.body().close();
                return null;
            }
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (declaredLength > MAX_AUDIO_BYTES) {
                response.body().close();
                AudioBoundsSystem.LOGGER.warn("ABS: audio response is too large: {} bytes", declaredLength);
                return null;
            }
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_AUDIO_BYTES + 1);
                if (bytes.length > MAX_AUDIO_BYTES) {
                    AudioBoundsSystem.LOGGER.warn("ABS: audio response exceeded {} bytes", MAX_AUDIO_BYTES);
                    return null;
                }
                return bytes;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Audio download interrupted: " + url, e);
        } catch (Exception e) {
            throw new IllegalStateException("Audio download failed: " + url, e);
        }
    }

    private void stopDownload(BlockPos pos) {
        DownloadOperation operation = downloads.remove(pos);
        if (operation != null) {
            operation.cancel();
        }
    }

    private String audioUrl(UUID token) {
        SocketAddress remote = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            remote = minecraft.getConnection().getConnection().getRemoteAddress();
        }
        return audioUrl(token, remote);
    }

    static String audioUrl(UUID token, SocketAddress remote) {
        String host = remote instanceof InetSocketAddress inet && inet.getHostString() != null
                ? inet.getHostString() : "localhost";
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        return "http://" + host + ":" + ABSHttpServer.DEFAULT_PORT + "/audio/" + token;
    }

    private static final class AudioDownloadThreadFactory implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "abs-audio-download-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class DownloadOperation {
        private volatile Future<?> task;
        private volatile boolean cancelled;

        private void setTask(Future<?> task) {
            this.task = task;
            if (cancelled) {
                task.cancel(true);
            }
        }

        private void cancel() {
            cancelled = true;
            Future<?> runningTask = task;
            if (runningTask != null) {
                runningTask.cancel(true);
            }
        }

        private boolean isCancelled() {
            return cancelled;
        }
    }
}
