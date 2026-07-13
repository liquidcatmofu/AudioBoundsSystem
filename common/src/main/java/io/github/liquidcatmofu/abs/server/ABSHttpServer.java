package io.github.liquidcatmofu.abs.server;

import com.sun.net.httpserver.HttpServer;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.server.web.AuthApiHandler;
import io.github.liquidcatmofu.abs.server.web.BlockConfigApiHandler;
import io.github.liquidcatmofu.abs.server.web.LibraryApiHandler;
import io.github.liquidcatmofu.abs.server.web.MeApiHandler;
import io.github.liquidcatmofu.abs.server.web.TtsApiHandler;
import io.github.liquidcatmofu.abs.server.web.WebUIHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ABSHttpServer {
    public static final int DEFAULT_PORT = 25566;

    private static volatile HttpServer server;
    private static ExecutorService executor;
    private static Path cacheDir;

    public static synchronized void start(MinecraftServer mcServer) throws IOException {
        if (server != null) return;

        cacheDir = mcServer.getWorldPath(LevelResource.ROOT).resolve("abs_cache");
        Files.createDirectories(cacheDir);

        HttpServer newServer = HttpServer.create(new InetSocketAddress(DEFAULT_PORT), 0);
        ExecutorService newExecutor = Executors.newFixedThreadPool(8, new AbsHttpThreadFactory());
        try {
            newServer.createContext("/api/auth",    new AuthApiHandler());
            newServer.createContext("/api/me",      new MeApiHandler(mcServer));
            newServer.createContext("/api/tts",     new TtsApiHandler());
            newServer.createContext("/api/library", new LibraryApiHandler(mcServer));
            newServer.createContext("/api/blocks",  new BlockConfigApiHandler(mcServer));
            newServer.createContext("/ui",          new WebUIHandler());
            newServer.setExecutor(newExecutor);
            newServer.start();
            server = newServer;
            executor = newExecutor;
        } catch (RuntimeException e) {
            newServer.stop(0);
            newExecutor.shutdownNow();
            throw e;
        }

        AudioBoundsSystem.LOGGER.info("ABS HTTP Server started on port {}", DEFAULT_PORT);
    }

    public static synchronized void stop() {
        HttpServer runningServer = server;
        ExecutorService runningExecutor = executor;
        server = null;
        executor = null;

        if (runningServer != null) {
            runningServer.stop(1);
        }
        if (runningExecutor != null) {
            runningExecutor.shutdown();
            try {
                if (!runningExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    runningExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                runningExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        TokenStore.clear();
        if (runningServer != null || runningExecutor != null) {
            AudioBoundsSystem.LOGGER.info("ABS HTTP Server stopped");
        }
    }

    /** 音声キャッシュディレクトリを返す（TTS 統合フェーズで使用） */
    public static Path getCacheDir() {
        return cacheDir;
    }

    /** メタデータ中の相対パスを abs_cache 内に限定して解決する。 */
    public static Optional<Path> resolveCacheFile(String cacheFile) {
        return resolveCacheFile(cacheDir, cacheFile);
    }

    static Optional<Path> resolveCacheFile(Path root, String cacheFile) {
        if (root == null || cacheFile == null || cacheFile.isBlank()) return Optional.empty();
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path resolved = normalizedRoot.resolve(cacheFile).normalize();
            // ライブラリ音源は abs_cache 直下だけを使用する。TTS の内部サブキャッシュは配信しない。
            if (!normalizedRoot.equals(resolved.getParent())) return Optional.empty();
            if (Files.exists(resolved)) {
                Path realRoot = normalizedRoot.toRealPath();
                Path realFile = resolved.toRealPath();
                if (!realRoot.equals(realFile.getParent())) return Optional.empty();
            }
            return Optional.of(resolved);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public static boolean isRunning() {
        return server != null;
    }

    private static final class AbsHttpThreadFactory implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "abs-http-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
