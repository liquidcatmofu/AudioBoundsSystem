package io.github.liquidcatmofu.abs.client.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.network.WebRpcProtocol;
import io.github.liquidcatmofu.abs.server.web.RequestBodyReader;
import io.github.liquidcatmofu.abs.server.web.WebAuthHelper;
import io.github.liquidcatmofu.abs.server.web.WebUIHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Loopback-only browser bridge. No server-side TCP listener is required. */
@Environment(EnvType.CLIENT)
public final class ClientWebServer {
    public static final ClientWebServer INSTANCE = new ClientWebServer();
    private static final String COOKIE_NAME = "abs_local";

    private HttpServer server;
    private ExecutorService executor;
    private UUID localToken;

    private ClientWebServer() {}

    public synchronized void open() {
        try {
            URI uri = createAuthUri();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                Component link = Component.literal("[ABS ダッシュボードを開く]")
                        .setStyle(Style.EMPTY.withUnderlined(true).withColor(0x55FFFF)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, uri.toString())));
                minecraft.player.sendSystemMessage(Component.empty()
                        .append(Component.literal("[ABS] ")).append(link));
            }
        } catch (IOException e) {
            AudioBoundsSystem.LOGGER.error("ABS: failed to start local WebUI", e);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("[ABS] ローカルWebUIを起動できませんでした。"));
            }
        }
    }

    synchronized URI createAuthUri() throws IOException {
        ensureStarted();
        localToken = UUID.randomUUID();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + "/auth/" + localToken);
    }

    public synchronized void stop() {
        HttpServer runningServer = server;
        ExecutorService runningExecutor = executor;
        server = null;
        executor = null;
        localToken = null;
        if (runningServer != null) runningServer.stop(0);
        if (runningExecutor != null) {
            runningExecutor.shutdownNow();
            try {
                runningExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    synchronized void ensureStarted() throws IOException {
        if (server != null) return;
        HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService createdExecutor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "abs-local-web");
            thread.setDaemon(true);
            return thread;
        });
        created.createContext("/auth", this::handleAuth);
        created.createContext("/api", this::handleApi);
        created.createContext("/ui", authenticated(new WebUIHandler()));
        created.setExecutor(createdExecutor);
        created.start();
        server = created;
        executor = createdExecutor;
        AudioBoundsSystem.LOGGER.info("ABS: local WebUI started on loopback port {}", created.getAddress().getPort());
    }

    synchronized boolean isRunning() {
        return server != null && executor != null && !executor.isShutdown();
    }

    synchronized int port() {
        if (server == null) throw new IllegalStateException("Client Web server is not running");
        return server.getAddress().getPort();
    }

    private void handleAuth(HttpExchange exchange) throws IOException {
        try {
            UUID expected = localToken;
            String path = exchange.getRequestURI().getPath();
            if (!"GET".equals(exchange.getRequestMethod()) || expected == null
                    || !path.equals("/auth/" + expected)) {
                sendJson(exchange, 403, "{\"error\":\"Invalid local WebUI token\"}");
                return;
            }
            exchange.getResponseHeaders().set("Set-Cookie", COOKIE_NAME + "=" + expected
                    + "; HttpOnly; Path=/; SameSite=Strict");
            exchange.getResponseHeaders().set("Location", "/ui");
            exchange.sendResponseHeaders(302, -1);
        } finally {
            exchange.close();
        }
    }

    private HttpHandler authenticated(HttpHandler next) {
        return exchange -> {
            try {
                if (!isAuthenticated(exchange)) {
                    sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                    return;
                }
                next.handle(exchange);
            } finally {
                exchange.close();
            }
        };
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        try {
            if (!isAuthenticated(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            byte[] body;
            try {
                body = RequestBodyReader.readBytes(exchange, WebRpcProtocol.MAX_BODY_BYTES);
            } catch (RequestBodyReader.PayloadTooLargeException e) {
                sendJson(exchange, 413, "{\"error\":\"Request body too large\"}");
                return;
            }

            String contentType = valueOrEmpty(exchange.getRequestHeaders().getFirst("Content-Type"));
            String filename = valueOrEmpty(exchange.getRequestHeaders().getFirst("X-Filename"));
            boolean csrf = "1".equals(exchange.getRequestHeaders().getFirst(WebAuthHelper.CSRF_HEADER));
            WebRpcClient.Response response;
            try {
                response = WebRpcClient.INSTANCE.request(exchange.getRequestMethod(),
                        exchange.getRequestURI().toString(), contentType, filename, csrf, body).get();
            } catch (Exception e) {
                AudioBoundsSystem.LOGGER.warn("ABS: local WebUI RPC failed", e);
                sendJson(exchange, 502, "{\"error\":\"Minecraft RPC failed\"}");
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", response.contentType());
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(response.status(), response.body().length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response.body());
            }
        } finally {
            exchange.close();
        }
    }

    private boolean isAuthenticated(HttpExchange exchange) {
        UUID expected = localToken;
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (expected == null || cookie == null) return false;
        for (String part : cookie.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && COOKIE_NAME.equals(pair[0]) && expected.toString().equals(pair[1])) {
                return true;
            }
        }
        return false;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
