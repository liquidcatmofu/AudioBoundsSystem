package io.github.liquidcatmofu.abs.server;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TokenStore {
    private static final long EXPIRY_MS = 60_000L;
    private static final ConcurrentHashMap<UUID, TokenEntry> store = new ConcurrentHashMap<>();

    private record TokenEntry(Path file, long expiryMs) {}

    public static UUID generate(Path oggFile) {
        purgeExpired();
        UUID token = UUID.randomUUID();
        store.put(token, new TokenEntry(oggFile, System.currentTimeMillis() + EXPIRY_MS));
        return token;
    }

    /** トークンが有効なら Path を返し、同時に削除する（単発使用） */
    public static Optional<Path> consume(UUID token) {
        TokenEntry entry = store.remove(token);
        if (entry == null || System.currentTimeMillis() > entry.expiryMs()) {
            return Optional.empty();
        }
        return Optional.of(entry.file());
    }

    public static void clear() {
        store.clear();
    }

    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now > e.getValue().expiryMs());
    }
}
