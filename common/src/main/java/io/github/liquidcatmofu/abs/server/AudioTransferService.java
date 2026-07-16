package io.github.liquidcatmofu.abs.server;

import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.audio.AudioContent;
import io.github.liquidcatmofu.abs.network.ABSNetwork;
import io.github.liquidcatmofu.abs.network.AudioTransferChunkPacket;
import io.github.liquidcatmofu.abs.network.AudioTransferErrorPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Sends authorized audio files through bounded Minecraft custom-payload chunks. */
public final class AudioTransferService {
    public static final int MAX_AUDIO_BYTES = 64 * 1024 * 1024;
    public static final int MAX_CHUNK_BYTES = 30 * 1024;
    private static final int MAX_TRANSFERS_PER_PLAYER = 2;
    private static final int MAX_ACTIVE_TRANSFERS = 2;
    private static final int CHUNKS_PER_BATCH = 8;
    private static final long BATCH_DELAY_MILLIS = 50;

    private static final TransferConcurrencyLimiter transferLimiter =
            new TransferConcurrencyLimiter(MAX_ACTIVE_TRANSFERS, MAX_TRANSFERS_PER_PLAYER);
    private static volatile ScheduledExecutorService executor;

    private AudioTransferService() {}

    public static synchronized void start() {
        if (executor != null) return;
        executor = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "abs-audio-transfer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static synchronized void stop() {
        ScheduledExecutorService running = executor;
        executor = null;
        TokenStore.clear();
        if (running == null) return;
        running.shutdownNow();
        try {
            running.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        transferLimiter.clear();
    }

    public static boolean isRunning() {
        return executor != null;
    }

    public static UUID generateToken(Path path, UUID playerUuid) {
        return TokenStore.generate(path, playerUuid);
    }

    public static void discard(UUID token, UUID playerUuid) {
        TokenStore.discard(token, playerUuid);
    }

    public static void request(ServerPlayer player, UUID token) {
        ScheduledExecutorService running = executor;
        if (running == null) {
            sendError(player, token, "Audio transfer service is stopped", true);
            return;
        }
        if (!transferLimiter.tryAcquire(player.getUUID())) {
            sendError(player, token, "Audio transfer service is busy", true);
            return;
        }
        try {
            running.execute(() -> {
                Path path = TokenStore.consume(token, player.getUUID()).orElse(null);
                if (path == null) {
                    sendError(player, token, "Audio request expired or was already used", false);
                    transferLimiter.release(player.getUUID());
                    return;
                }
                try {
                    byte[] audio = readAudio(path);
                    sendBatch(running, player, token, audio, 0);
                } catch (Exception e) {
                    AudioBoundsSystem.LOGGER.warn("ABS: failed to transfer audio for {}", player.getGameProfile().getName(), e);
                    sendError(player, token, "Audio transfer failed", false);
                    transferLimiter.release(player.getUUID());
                }
            });
        } catch (RejectedExecutionException e) {
            transferLimiter.release(player.getUUID());
            sendError(player, token, "Audio transfer service is stopping", true);
        }
    }

    private static void sendBatch(ScheduledExecutorService running, ServerPlayer player, UUID token,
                                  byte[] audio, int startOffset) {
        int offset = startOffset;
        try {
            for (int sent = 0; sent < CHUNKS_PER_BATCH && offset < audio.length; sent++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                int end = Math.min(audio.length, offset + MAX_CHUNK_BYTES);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                new AudioTransferChunkPacket(token, audio.length, offset,
                        Arrays.copyOfRange(audio, offset, end)).write(buf);
                NetworkManager.sendToPlayer(player, ABSNetwork.AUDIO_TRANSFER_CHUNK, buf);
                offset = end;
            }

            if (offset >= audio.length) {
                transferLimiter.release(player.getUUID());
                return;
            }
            int nextOffset = offset;
            running.schedule(() -> sendBatch(running, player, token, audio, nextOffset),
                    BATCH_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendError(player, token, "Audio transfer interrupted", false);
            transferLimiter.release(player.getUUID());
        } catch (RejectedExecutionException e) {
            sendError(player, token, "Audio transfer service is stopping", true);
            transferLimiter.release(player.getUUID());
        } catch (Exception e) {
            AudioBoundsSystem.LOGGER.warn("ABS: failed to send audio chunks to {}", player.getGameProfile().getName(), e);
            sendError(player, token, "Audio transfer failed", false);
            transferLimiter.release(player.getUUID());
        }
    }

    private static byte[] readAudio(Path path) throws IOException {
        long declaredSize = Files.size(path);
        if (declaredSize < 1 || declaredSize > MAX_AUDIO_BYTES) {
            throw new IOException("Audio file size is out of bounds: " + declaredSize);
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_AUDIO_BYTES + 1);
        }
        if (bytes.length > MAX_AUDIO_BYTES) throw new IOException("Audio file exceeds transfer limit");
        if (bytes.length != declaredSize) throw new IOException("Audio file changed while being read");
        AudioContent.requireOgg(bytes);
        return bytes;
    }

    private static void sendError(ServerPlayer player, UUID token, String message, boolean retryable) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new AudioTransferErrorPacket(token, message, retryable).write(buf);
        NetworkManager.sendToPlayer(player, ABSNetwork.AUDIO_TRANSFER_ERROR, buf);
    }

}
