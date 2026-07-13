package io.github.liquidcatmofu.abs.server.web;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WebSessionStore {
    private static final long TTL_MS = 8 * 60 * 60 * 1000L;
    private static final ConcurrentHashMap<UUID, Entry> store = new ConcurrentHashMap<>();

    private record Entry(UUID playerUuid, long expiryMs) {}

    private WebSessionStore() {}

    /** Minecraft RPC transport用に、接続済みプレイヤーへ直接セッションを発行する。 */
    public static UUID createSession(UUID playerUuid) {
        purgeExpired();
        UUID sessionToken = UUID.randomUUID();
        store.put(sessionToken, new Entry(playerUuid, System.currentTimeMillis() + TTL_MS));
        return sessionToken;
    }

    public static Optional<UUID> getPlayerUuid(UUID sessionToken) {
        Entry entry = store.get(sessionToken);
        if (entry == null || System.currentTimeMillis() > entry.expiryMs()) {
            store.remove(sessionToken);
            return Optional.empty();
        }
        return Optional.of(entry.playerUuid());
    }

    public static void invalidate(UUID sessionToken) {
        store.remove(sessionToken);
    }

    public static void clear() {
        store.clear();
    }

    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now > e.getValue().expiryMs());
    }
}
