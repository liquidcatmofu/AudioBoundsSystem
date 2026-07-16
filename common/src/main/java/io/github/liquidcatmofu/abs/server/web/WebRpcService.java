package io.github.liquidcatmofu.abs.server.web;

import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.network.ABSNetwork;
import io.github.liquidcatmofu.abs.network.WebRpcProtocol;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Reassembles bounded WebUI requests and dispatches them through {@link WebApiRouter}. */
public final class WebRpcService {
    private static final int MAX_REQUESTS_PER_PLAYER = 4;
    private static final int CHUNKS_PER_BATCH = 8;
    private static final long BATCH_DELAY_MILLIS = 50;
    private static final long REQUEST_EXPIRY_MILLIS = 120_000;
    private static final int MEMORY_BODY_THRESHOLD = RequestBodyReader.MAX_JSON_BYTES;

    private static final Map<RequestKey, RequestState> requests = new ConcurrentHashMap<>();
    private static final Map<UUID, AtomicInteger> playerRequestCounts = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> playerSessions = new ConcurrentHashMap<>();
    private static volatile ScheduledExecutorService scheduler;
    private static volatile ExecutorService requestWorkers;
    private static volatile WebApiRouter router;

    private WebRpcService() {}

    public static synchronized void start(MinecraftServer server) {
        if (scheduler != null) return;
        router = new WebApiRouter(server);
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "abs-web-rpc-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        requestWorkers = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "abs-web-rpc-worker");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(WebRpcService::purgeExpired,
                30, 30, TimeUnit.SECONDS);
    }

    public static synchronized void stop() {
        ScheduledExecutorService runningScheduler = scheduler;
        ExecutorService runningWorkers = requestWorkers;
        scheduler = null;
        requestWorkers = null;
        router = null;
        requests.values().forEach(state -> closeBody(state.body()));
        requests.clear();
        playerRequestCounts.clear();
        playerSessions.clear();
        if (runningWorkers != null) runningWorkers.shutdownNow();
        if (runningScheduler != null) runningScheduler.shutdownNow();
        try {
            if (runningWorkers != null) runningWorkers.awaitTermination(5, TimeUnit.SECONDS);
            if (runningScheduler != null) runningScheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static synchronized boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown()
                && requestWorkers != null && !requestWorkers.isShutdown();
    }

    public static void playerDisconnected(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        for (Map.Entry<RequestKey, RequestState> entry : requests.entrySet()) {
            if (!entry.getKey().playerUuid().equals(playerUuid)) continue;
            RequestState state = entry.getValue();
            if (requests.remove(entry.getKey(), state)) closeBody(state.body());
        }
        playerRequestCounts.remove(playerUuid);
        UUID session = playerSessions.remove(playerUuid);
        if (session != null) WebSessionStore.invalidate(session);
    }

    public static void startRequest(ServerPlayer player, UUID requestId, String method, String path,
                                    String contentType, String filename, boolean csrfHeader,
                                    int totalLength, byte[] digest) {
        if (scheduler == null || requestWorkers == null || router == null) {
            sendError(player, requestId, 503, "WebUI RPC service is stopped");
            return;
        }
        String validationError = validateMetadata(method, path, totalLength, digest);
        if (validationError != null) {
            sendError(player, requestId, 400, validationError);
            return;
        }

        UUID playerUuid = player.getUUID();
        AtomicInteger count = playerRequestCounts.computeIfAbsent(playerUuid, ignored -> new AtomicInteger());
        if (count.incrementAndGet() > MAX_REQUESTS_PER_PLAYER) {
            releasePlayer(playerUuid, count);
            sendError(player, requestId, 429, "Too many concurrent WebUI requests");
            return;
        }

        RequestKey key = new RequestKey(playerUuid, requestId);
        WebRpcRequestBody body;
        try {
            body = totalLength == 0 ? null
                    : new WebRpcRequestBody(totalLength, MEMORY_BODY_THRESHOLD, WebRpcProtocol.MAX_BODY_BYTES);
        } catch (IOException e) {
            releasePlayer(playerUuid, count);
            sendError(player, requestId, 500, "Could not prepare request storage");
            return;
        }
        RequestState state = new RequestState(player, method, URI.create(path), contentType, filename,
                csrfHeader, totalLength, digest, System.currentTimeMillis(), body);
        if (requests.putIfAbsent(key, state) != null) {
            closeBody(body);
            releasePlayer(playerUuid, count);
            sendError(player, requestId, 409, "Duplicate WebUI request id");
            return;
        }

        if (totalLength == 0) {
            requests.remove(key, state);
            releasePlayer(playerUuid, count);
            if (!WebRpcProtocol.digestMatches(new byte[0], digest)) {
                sendError(player, requestId, 400, "Request checksum mismatch");
            } else {
                dispatch(requestId, state);
            }
        }
    }

    public static void acceptChunk(ServerPlayer player, UUID requestId, int totalLength,
                                   int offset, byte[] chunk) {
        RequestKey key = new RequestKey(player.getUUID(), requestId);
        RequestState state = requests.get(key);
        if (state == null) return;
        try {
            if (!state.body().accept(totalLength, offset, chunk)) return;
            if (!requests.remove(key, state)) return;
            releasePlayer(player.getUUID(), playerRequestCounts.get(player.getUUID()));
            if (!state.body().digestMatches(state.digest())) {
                closeBody(state.body());
                sendError(player, requestId, 400, "Request checksum mismatch");
                return;
            }
            dispatch(requestId, state);
        } catch (IOException e) {
            if (requests.remove(key, state)) {
                releasePlayer(player.getUUID(), playerRequestCounts.get(player.getUUID()));
            }
            closeBody(state.body());
            sendError(player, requestId, 400, e.getMessage());
        }
    }

    private static void dispatch(UUID requestId, RequestState state) {
        ExecutorService running = requestWorkers;
        WebApiRouter currentRouter = router;
        if (running == null || currentRouter == null) {
            closeBody(state.body());
            sendError(state.player(), requestId, 503, "WebUI RPC service is stopped");
            return;
        }
        try {
            running.execute(() -> {
                try (WebRpcRequestBody body = state.body();
                     InputStream input = body == null ? InputStream.nullInputStream() : body.openStream()) {
                    MemoryHttpExchange exchange = new MemoryHttpExchange(state.method(), state.uri(), input);
                    exchange.getRequestHeaders().set("Content-Length", Integer.toString(state.totalLength()));
                    exchange.getRequestHeaders().set("Cookie", WebAuthHelper.sessionCookieHeader(
                            sessionFor(state.player().getUUID())));
                    if (!state.contentType().isBlank()) {
                        exchange.getRequestHeaders().set("Content-Type", state.contentType());
                    }
                    if (!state.filename().isBlank()) {
                        exchange.getRequestHeaders().set("X-Filename", state.filename());
                    }
                    if (state.csrfHeader()) {
                        exchange.getRequestHeaders().set(WebAuthHelper.CSRF_HEADER, "1");
                    }
                    currentRouter.handle(exchange);
                    int status = exchange.getResponseCode() < 0 ? 500 : exchange.getResponseCode();
                    String responseType = exchange.getResponseHeaders().getFirst("Content-Type");
                    sendResponse(state.player(), requestId, status,
                            responseType == null ? "application/octet-stream" : responseType,
                            exchange.responseBytes());
                } catch (Exception e) {
                    AudioBoundsSystem.LOGGER.error("ABS: WebUI RPC request failed", e);
                    sendError(state.player(), requestId, 500, "Internal Server Error");
                }
            });
        } catch (RejectedExecutionException e) {
            closeBody(state.body());
            sendError(state.player(), requestId, 503, "WebUI RPC service is stopping");
        }
    }

    private static void sendResponse(ServerPlayer player, UUID requestId, int status,
                                     String contentType, byte[] body) {
        if (body.length > WebRpcProtocol.MAX_BODY_BYTES) {
            sendError(player, requestId, 500, "WebUI response body is too large");
            return;
        }
        FriendlyByteBuf start = new FriendlyByteBuf(Unpooled.buffer());
        start.writeUUID(requestId);
        start.writeVarInt(status);
        start.writeUtf(contentType, 128);
        start.writeVarInt(body.length);
        start.writeByteArray(WebRpcProtocol.sha256(body));
        NetworkManager.sendToPlayer(player, ABSNetwork.WEB_RPC_RESPONSE_START, start);
        if (body.length == 0) return;

        ScheduledExecutorService running = scheduler;
        if (running != null) sendResponseBatch(running, player, requestId, body, 0);
    }

    private static void sendResponseBatch(ScheduledExecutorService running, ServerPlayer player,
                                          UUID requestId, byte[] body, int startOffset) {
        int offset = startOffset;
        try {
            for (int sent = 0; sent < CHUNKS_PER_BATCH && offset < body.length; sent++) {
                byte[] chunk = WebRpcProtocol.chunk(body, offset);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeUUID(requestId);
                buf.writeVarInt(body.length);
                buf.writeVarInt(offset);
                buf.writeByteArray(chunk);
                NetworkManager.sendToPlayer(player, ABSNetwork.WEB_RPC_RESPONSE_CHUNK, buf);
                offset += chunk.length;
            }
            if (offset < body.length) {
                int nextOffset = offset;
                running.schedule(() -> sendResponseBatch(running, player, requestId, body, nextOffset),
                        BATCH_DELAY_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (RejectedExecutionException ignored) {
            // Server shutdown cancels the client-side request through disconnect handling.
        }
    }

    private static void sendError(ServerPlayer player, UUID requestId, int status, String message) {
        String escaped = message == null ? "Request failed"
                : message.replace("\\", "\\\\").replace("\"", "\\\"");
        sendResponse(player, requestId, status, "application/json; charset=utf-8",
                ("{\"error\":\"" + escaped + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    static String validateMetadata(String method, String path, int totalLength, byte[] digest) {
        if (!("GET".equals(method) || "POST".equals(method) || "PATCH".equals(method)
                || "DELETE".equals(method))) return "Unsupported request method";
        try {
            URI uri = URI.create(path);
            String apiPath = uri.getPath();
            if (uri.isAbsolute() || uri.getHost() != null || apiPath == null
                    || !(apiPath.equals("/api") || apiPath.startsWith("/api/"))
                    || hasRelativePathSegment(apiPath)) {
                return "Invalid API path";
            }
        } catch (RuntimeException e) {
            return "Invalid API path";
        }
        if (totalLength < 0 || totalLength > WebRpcProtocol.MAX_BODY_BYTES) return "Request body is too large";
        if (digest == null || digest.length != WebRpcProtocol.DIGEST_BYTES) return "Invalid request checksum";
        return null;
    }

    private static boolean hasRelativePathSegment(String path) {
        for (String segment : path.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) return true;
        }
        return false;
    }

    private static UUID sessionFor(UUID playerUuid) {
        return playerSessions.compute(playerUuid, (ignored, existing) ->
                existing != null && WebSessionStore.getPlayerUuid(existing).isPresent()
                        ? existing : WebSessionStore.createSession(playerUuid));
    }

    private static void purgeExpired() {
        long cutoff = System.currentTimeMillis() - REQUEST_EXPIRY_MILLIS;
        for (Map.Entry<RequestKey, RequestState> entry : requests.entrySet()) {
            RequestState state = entry.getValue();
            if (state.createdAtMillis() >= cutoff || !requests.remove(entry.getKey(), state)) continue;
            releasePlayer(entry.getKey().playerUuid(), playerRequestCounts.get(entry.getKey().playerUuid()));
            closeBody(state.body());
            sendError(state.player(), entry.getKey().requestId(), 408, "WebUI request timed out");
        }
    }

    private static void closeBody(WebRpcRequestBody body) {
        if (body == null) return;
        try {
            body.close();
        } catch (IOException e) {
            AudioBoundsSystem.LOGGER.warn("ABS: failed to clean up WebUI request body", e);
        }
    }

    private static void releasePlayer(UUID playerUuid, AtomicInteger count) {
        if (count != null && count.decrementAndGet() <= 0) {
            playerRequestCounts.remove(playerUuid, count);
        }
    }

    private record RequestKey(UUID playerUuid, UUID requestId) {}

    private record RequestState(ServerPlayer player, String method, URI uri, String contentType,
                                String filename, boolean csrfHeader, int totalLength, byte[] digest,
                                long createdAtMillis, WebRpcRequestBody body) {}
}
