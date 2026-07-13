package io.github.liquidcatmofu.abs.client.audio;

import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.audio.AudioContent;
import io.github.liquidcatmofu.abs.client.sound.ABSDynamicSoundStore;
import io.github.liquidcatmofu.abs.client.sound.ABSSpeakerSoundInstance;
import io.github.liquidcatmofu.abs.network.ABSNetwork;
import io.github.liquidcatmofu.abs.network.ChunkedTransferAssembler;
import io.github.liquidcatmofu.abs.server.AudioTransferService;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Environment(EnvType.CLIENT)
public final class SpeakerAudioManager {
    public static final SpeakerAudioManager INSTANCE = new SpeakerAudioManager();
    private static final long REQUEST_TIMEOUT_SECONDS = 30;

    private final Map<BlockPos, ABSSpeakerSoundInstance> activeSources = new HashMap<>();
    private final Map<BlockPos, DownloadOperation> downloads = new HashMap<>();
    private final ConcurrentHashMap<UUID, DownloadOperation> transfers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> hashLocks = new ConcurrentHashMap<>();
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2, new AudioDownloadThreadFactory());
    private volatile ClientAudioCache diskCache;
    private volatile boolean diskCacheInitializationAttempted;

    private SpeakerAudioManager() {}

    public void play(BlockPos pos, UUID token, String contentHash) {
        stopDownload(pos);
        String verifiedHash = ClientAudioCache.isValidHash(contentHash) ? contentHash : "";
        DownloadOperation operation = new DownloadOperation(token);
        downloads.put(pos, operation);
        transfers.put(token, operation);
        operation.setTask(downloadExecutor.submit(() -> {
            byte[] bytes = null;
            Throwable error = null;
            try {
                bytes = loadAudio(token, verifiedHash, operation);
            } catch (Exception e) {
                error = e;
            }
            byte[] completedBytes = bytes;
            Throwable completedError = error;
            Minecraft.getInstance().execute(() -> {
                    if (downloads.get(pos) != operation) {
                        transfers.remove(token, operation);
                        return;
                    }
                    downloads.remove(pos);
                    transfers.remove(token, operation);
                    if (completedError != null) {
                        if (!operation.isCancelled()) {
                            AudioBoundsSystem.LOGGER.error("ABS: failed to receive speaker audio", completedError);
                        }
                        return;
                    }
                    if (completedBytes == null) {
                        return;
                    }

                    stop(pos);

                    String resourceKey = verifiedHash.isEmpty() ? token.toString() : verifiedHash;
                    ResourceLocation dynamicLoc = new ResourceLocation("abs", "sounds/dynamic/" + resourceKey + ".ogg");
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

    public void acceptTransferChunk(UUID token, int totalLength, int offset, byte[] chunk) {
        DownloadOperation operation = transfers.get(token);
        if (operation == null) return;
        try {
            byte[] complete = operation.assembler.accept(totalLength, offset, chunk);
            if (complete != null) {
                transfers.remove(token, operation);
                operation.complete(complete);
            }
        } catch (IOException e) {
            transfers.remove(token, operation);
            operation.fail(e);
        }
    }

    public void failTransfer(UUID token, String message) {
        DownloadOperation operation = transfers.get(token);
        if (operation != null && transfers.remove(token, operation)) {
            operation.fail(new IOException(message));
        }
    }

    public void stop(BlockPos pos) {
        stopDownload(pos);
        ABSSpeakerSoundInstance instance = activeSources.remove(pos);
        if (instance == null) {
            return;
        }

        Minecraft.getInstance().getSoundManager().stop(instance);
        ResourceLocation resource = instance.getDynamicResourceLoc();
        boolean stillUsed = activeSources.values().stream()
                .anyMatch(active -> resource.equals(active.getDynamicResourceLoc()));
        if (!stillUsed) {
            ABSDynamicSoundStore.remove(resource);
        }
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
        transfers.clear();
        new ArrayList<>(activeSources.keySet()).forEach(this::stop);
    }

    private byte[] requestAudio(UUID token, DownloadOperation operation) throws IOException, InterruptedException {
        Minecraft.getInstance().execute(() -> {
            if (operation.isCancelled()) return;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUUID(token);
            NetworkManager.sendToServer(ABSNetwork.REQUEST_AUDIO_TRANSFER, buf);
        });
        try {
            return operation.result.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException("Audio transfer failed", cause);
        } catch (TimeoutException e) {
            throw new IOException("Audio transfer timed out", e);
        }
    }

    private byte[] loadAudio(UUID token, String contentHash, DownloadOperation operation)
            throws IOException, InterruptedException {
        if (contentHash.isEmpty()) {
            byte[] downloaded = requestAudio(token, operation);
            if (downloaded != null) AudioContent.requireOgg(downloaded);
            return downloaded;
        }

        ReentrantLock lock = hashLocks.computeIfAbsent(contentHash, ignored -> new ReentrantLock());
        lock.lockInterruptibly();
        try {
            ClientAudioCache cache = diskCache();
            if (cache != null) {
                var cached = cache.get(contentHash);
                if (cached.isPresent()) {
                    AudioBoundsSystem.LOGGER.debug("ABS: client audio cache hit {}", contentHash);
                    return cached.get();
                }
            }

            byte[] downloaded = requestAudio(token, operation);
            if (downloaded == null) return null;
            AudioContent.requireOgg(downloaded);
            if (!contentHash.equals(AudioContent.sha256(downloaded))) {
                throw new IOException("Downloaded audio hash does not match play metadata");
            }
            if (cache != null) {
                try {
                    cache.put(contentHash, downloaded);
                } catch (IOException e) {
                    AudioBoundsSystem.LOGGER.warn("ABS: failed to write client audio cache", e);
                }
            }
            return downloaded;
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                hashLocks.remove(contentHash, lock);
            }
        }
    }

    private ClientAudioCache diskCache() {
        if (diskCache != null || diskCacheInitializationAttempted) {
            return diskCache;
        }
        synchronized (this) {
            if (diskCache != null || diskCacheInitializationAttempted) {
                return diskCache;
            }
            diskCacheInitializationAttempted = true;
            try {
                var root = Minecraft.getInstance().gameDirectory.toPath()
                        .resolve("abs_client_cache").resolve("audio");
                diskCache = new ClientAudioCache(root);
            } catch (IOException e) {
                AudioBoundsSystem.LOGGER.warn("ABS: client audio disk cache is unavailable", e);
            }
            return diskCache;
        }
    }

    private void stopDownload(BlockPos pos) {
        DownloadOperation operation = downloads.remove(pos);
        if (operation != null) {
            transfers.remove(operation.token, operation);
            operation.cancel();
        }
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
        private final ChunkedTransferAssembler assembler =
                new ChunkedTransferAssembler(AudioTransferService.MAX_AUDIO_BYTES);
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final UUID token;
        private volatile Future<?> task;
        private volatile boolean cancelled;

        private DownloadOperation(UUID token) {
            this.token = token;
        }

        private void setTask(Future<?> task) {
            this.task = task;
            if (cancelled) {
                task.cancel(true);
            }
        }

        private void cancel() {
            cancelled = true;
            result.cancel(true);
            Future<?> runningTask = task;
            if (runningTask != null) {
                runningTask.cancel(true);
            }
        }

        private boolean isCancelled() {
            return cancelled;
        }

        private void complete(byte[] bytes) {
            result.complete(bytes);
        }

        private void fail(Throwable error) {
            result.completeExceptionally(error);
        }
    }
}
