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
import java.util.UUID;
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
            newServer.createContext("/audio",       new AudioRequestHandler());
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

    /** Ogg ファイルに対するワンタイムトークンを発行する */
    public static UUID generateToken(Path oggFile) {
        return TokenStore.generate(oggFile);
    }

    /** 音声キャッシュディレクトリを返す（TTS 統合フェーズで使用） */
    public static Path getCacheDir() {
        return cacheDir;
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
