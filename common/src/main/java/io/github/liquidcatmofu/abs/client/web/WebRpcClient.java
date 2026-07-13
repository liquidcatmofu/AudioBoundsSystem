package io.github.liquidcatmofu.abs.client.web;

import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.network.ABSNetwork;
import io.github.liquidcatmofu.abs.network.ChunkedTransferAssembler;
import io.github.liquidcatmofu.abs.network.WebRpcProtocol;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Client endpoint for bounded WebUI request/response RPC over Minecraft networking. */
@Environment(EnvType.CLIENT)
public final class WebRpcClient {
    public static final WebRpcClient INSTANCE = new WebRpcClient();
    private static final int CHUNKS_PER_BATCH = 8;
    private static final long BATCH_DELAY_MILLIS = 50;
    private static final long REQUEST_TIMEOUT_SECONDS = 120;

    private final ConcurrentHashMap<UUID, PendingResponse> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "abs-web-rpc-client");
        thread.setDaemon(true);
        return thread;
    });

    private WebRpcClient() {}

    public CompletableFuture<Response> request(String method, String path, String contentType,
                                               String filename, boolean csrfHeader, byte[] body) {
        if (body == null) body = new byte[0];
        if (body.length > WebRpcProtocol.MAX_BODY_BYTES) {
            return CompletableFuture.failedFuture(new IOException("WebUI request body is too large"));
        }

        UUID requestId = UUID.randomUUID();
        PendingResponse response = new PendingResponse();
        pending.put(requestId, response);
        response.future.orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> pending.remove(requestId, response));

        FriendlyByteBuf start = new FriendlyByteBuf(Unpooled.buffer());
        start.writeUUID(requestId);
        start.writeUtf(method, 8);
        start.writeUtf(path, 1024);
        start.writeUtf(contentType == null ? "" : contentType, 128);
        start.writeUtf(filename == null ? "" : filename, 1024);
        start.writeBoolean(csrfHeader);
        start.writeVarInt(body.length);
        start.writeByteArray(WebRpcProtocol.sha256(body));
        NetworkManager.sendToServer(ABSNetwork.WEB_RPC_REQUEST_START, start);
        if (body.length > 0) sendRequestBatch(requestId, body, 0);
        return response.future;
    }

    public void startResponse(UUID requestId, int status, String contentType,
                              int totalLength, byte[] digest) {
        PendingResponse response = pending.get(requestId);
        if (response == null) return;
        try {
            response.start(status, contentType, totalLength, digest);
            if (totalLength == 0) finish(requestId, response, new byte[0]);
        } catch (IOException e) {
            fail(requestId, response, e);
        }
    }

    public void acceptResponseChunk(UUID requestId, int totalLength, int offset, byte[] chunk) {
        PendingResponse response = pending.get(requestId);
        if (response == null) return;
        try {
            byte[] complete = response.accept(totalLength, offset, chunk);
            if (complete != null) finish(requestId, response, complete);
        } catch (IOException e) {
            fail(requestId, response, e);
        }
    }

    public void clear() {
        IOException error = new IOException("Disconnected during WebUI request");
        pending.forEach((ignored, response) -> response.future.completeExceptionally(error));
        pending.clear();
    }

    private void sendRequestBatch(UUID requestId, byte[] body, int startOffset) {
        if (!pending.containsKey(requestId)) return;
        int offset = startOffset;
        for (int sent = 0; sent < CHUNKS_PER_BATCH && offset < body.length; sent++) {
            byte[] chunk = WebRpcProtocol.chunk(body, offset);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUUID(requestId);
            buf.writeVarInt(body.length);
            buf.writeVarInt(offset);
            buf.writeByteArray(chunk);
            NetworkManager.sendToServer(ABSNetwork.WEB_RPC_REQUEST_CHUNK, buf);
            offset += chunk.length;
        }
        if (offset < body.length) {
            int nextOffset = offset;
            sender.schedule(() -> sendRequestBatch(requestId, body, nextOffset),
                    BATCH_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private void finish(UUID requestId, PendingResponse response, byte[] body) throws IOException {
        if (!WebRpcProtocol.digestMatches(body, response.digest)) {
            throw new IOException("WebUI response checksum mismatch");
        }
        if (pending.remove(requestId, response)) {
            response.future.complete(new Response(response.status, response.contentType, body));
        }
    }

    private void fail(UUID requestId, PendingResponse response, IOException error) {
        if (pending.remove(requestId, response)) response.future.completeExceptionally(error);
    }

    public record Response(int status, String contentType, byte[] body) {}

    private static final class PendingResponse {
        private final CompletableFuture<Response> future = new CompletableFuture<>();
        private ChunkedTransferAssembler assembler;
        private int status;
        private String contentType;
        private byte[] digest;

        private synchronized void start(int status, String contentType, int totalLength, byte[] digest)
                throws IOException {
            if (assembler != null || this.digest != null) throw new IOException("Duplicate WebUI response start");
            if (totalLength < 0 || totalLength > WebRpcProtocol.MAX_BODY_BYTES
                    || digest == null || digest.length != WebRpcProtocol.DIGEST_BYTES) {
                throw new IOException("Invalid WebUI response metadata");
            }
            this.status = status;
            this.contentType = contentType;
            this.digest = digest;
            this.assembler = new ChunkedTransferAssembler(WebRpcProtocol.MAX_BODY_BYTES);
        }

        private synchronized byte[] accept(int totalLength, int offset, byte[] chunk) throws IOException {
            if (assembler == null) throw new IOException("WebUI response chunk arrived before metadata");
            return assembler.accept(totalLength, offset, chunk);
        }
    }
}
