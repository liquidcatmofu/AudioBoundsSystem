package io.github.liquidcatmofu.abs.server;

import com.sun.net.httpserver.HttpServer;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;

public class ABSHttpServer {
    public static final int DEFAULT_PORT = 25566;

    private static HttpServer server;
    private static Path cacheDir;

    public static void start(MinecraftServer mcServer) throws IOException {
        if (server != null) return;

        cacheDir = mcServer.getServerDirectory().toPath().resolve("abs_cache");
        Files.createDirectories(cacheDir);

        server = HttpServer.create(new InetSocketAddress(DEFAULT_PORT), 0);
        server.createContext("/audio", new AudioRequestHandler());
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.start();

        AudioBoundsSystem.LOGGER.info("ABS HTTP Server started on port {}", DEFAULT_PORT);
    }

    public static void stop() {
        if (server == null) return;
        server.stop(1);
        server = null;
        TokenStore.clear();
        AudioBoundsSystem.LOGGER.info("ABS HTTP Server stopped");
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
}
