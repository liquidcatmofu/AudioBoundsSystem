package io.github.liquidcatmofu.abs.server.web;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WebSessionStore {
    static final long TTL_MS = 8 * 60 * 60 * 1000L;
    private static final ConcurrentHashMap<UUID, Entry> store = new ConcurrentHashMap<>();

    private record Entry(UUID playerUuid, long expiryMs) {}

    private WebSessionStore() {}

    /** Minecraft RPC transport用に、接続済みプレイヤーへ直接セッションを発行する。 */
    public static UUID createSession(UUID playerUuid) {
        return createSession(playerUuid, System.currentTimeMillis());
    }

    static UUID createSession(UUID playerUuid, long nowMs) {
        purgeExpired(nowMs);
        UUID sessionToken = UUID.randomUUID();
        store.put(sessionToken, new Entry(playerUuid, nowMs + TTL_MS));
        return sessionToken;
    }

    public static Optional<UUID> getPlayerUuid(UUID sessionToken) {
        return getPlayerUuid(sessionToken, System.currentTimeMillis());
    }

    static Optional<UUID> getPlayerUuid(UUID sessionToken, long nowMs) {
        if (sessionToken == null) return Optional.empty();
        Entry entry = store.get(sessionToken);
        if (entry == null || nowMs > entry.expiryMs()) {
            store.remove(sessionToken);
            return Optional.empty();
        }
        return Optional.of(entry.playerUuid());
    }

    public static void invalidate(UUID sessionToken) {
        if (sessionToken == null) return;
        store.remove(sessionToken);
    }

    public static void clear() {
        store.clear();
    }

    private static void purgeExpired(long nowMs) {
        store.entrySet().removeIf(e -> nowMs > e.getValue().expiryMs());
    }
}
