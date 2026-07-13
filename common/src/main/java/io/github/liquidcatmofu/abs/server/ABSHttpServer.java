package io.github.liquidcatmofu.abs.server;

import com.sun.net.httpserver.HttpServer;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.server.web.AuthApiHandler;
import io.github.liquidcatmofu.abs.server.web.WebUIHandler;
import io.github.liquidcatmofu.abs.server.web.WebApiRouter;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ABSHttpServer {
    public static final int DEFAULT_PORT = 25566;

    private static volatile HttpServer server;
    private static ExecutorService executor;
    public static synchronized void start(MinecraftServer mcServer) throws IOException {
        if (server != null) return;

        HttpServer newServer = HttpServer.create(new InetSocketAddress(DEFAULT_PORT), 0);
        ExecutorService newExecutor = Executors.newFixedThreadPool(8, new AbsHttpThreadFactory());
        try {
            newServer.createContext("/api/auth",    new AuthApiHandler());
            newServer.createContext("/api",         new WebApiRouter(mcServer));
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
        if (runningServer != null || runningExecutor != null) {
            AudioBoundsSystem.LOGGER.info("ABS HTTP Server stopped");
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
