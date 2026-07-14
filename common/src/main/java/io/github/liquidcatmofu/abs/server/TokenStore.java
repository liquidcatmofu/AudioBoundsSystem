package io.github.liquidcatmofu.abs.server;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TokenStore {
    static final long EXPIRY_MS = 60_000L;
    private static final ConcurrentHashMap<UUID, TokenEntry> store = new ConcurrentHashMap<>();

    private record TokenEntry(Path file, long expiryMs) {}

    public static UUID generate(Path oggFile) {
        return generate(oggFile, System.currentTimeMillis());
    }

    static UUID generate(Path oggFile, long nowMs) {
        purgeExpired(nowMs);
        UUID token = UUID.randomUUID();
        store.put(token, new TokenEntry(oggFile, nowMs + EXPIRY_MS));
        return token;
    }

    /** トークンが有効なら Path を返し、同時に削除する（単発使用） */
    public static Optional<Path> consume(UUID token) {
        return consume(token, System.currentTimeMillis());
    }

    static Optional<Path> consume(UUID token, long nowMs) {
        if (token == null) return Optional.empty();
        TokenEntry entry = store.remove(token);
        if (entry == null || nowMs > entry.expiryMs()) {
            return Optional.empty();
        }
        return Optional.of(entry.file());
    }

    public static void clear() {
        store.clear();
    }

    private static void purgeExpired(long nowMs) {
        store.entrySet().removeIf(e -> nowMs > e.getValue().expiryMs());
    }
}
