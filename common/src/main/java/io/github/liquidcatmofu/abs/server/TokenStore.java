package io.github.liquidcatmofu.abs.server;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TokenStore {
    static final long EXPIRY_MS = 60_000L;
    private static final ConcurrentHashMap<UUID, TokenEntry> store = new ConcurrentHashMap<>();

    private record TokenEntry(Path file, UUID playerUuid, long expiryMs) {}

    public static UUID generate(Path oggFile, UUID playerUuid) {
        return generate(oggFile, playerUuid, System.currentTimeMillis());
    }

    static UUID generate(Path oggFile, UUID playerUuid, long nowMs) {
        if (oggFile == null || playerUuid == null) throw new IllegalArgumentException("Audio token requires file and player");
        purgeExpired(nowMs);
        UUID token = UUID.randomUUID();
        store.put(token, new TokenEntry(oggFile, playerUuid, nowMs + EXPIRY_MS));
        return token;
    }

    /** トークンが有効なら Path を返し、同時に削除する（単発使用） */
    public static Optional<Path> consume(UUID token, UUID playerUuid) {
        return consume(token, playerUuid, System.currentTimeMillis());
    }

    static Optional<Path> consume(UUID token, UUID playerUuid, long nowMs) {
        if (token == null || playerUuid == null) return Optional.empty();
        TokenEntry entry = store.get(token);
        if (entry == null || !playerUuid.equals(entry.playerUuid())) return Optional.empty();
        if (!store.remove(token, entry) || nowMs > entry.expiryMs()) {
            return Optional.empty();
        }
        return Optional.of(entry.file());
    }

    public static void clear() {
        store.clear();
    }

    static void discard(UUID token, UUID playerUuid) {
        if (token == null || playerUuid == null) return;
        TokenEntry entry = store.get(token);
        if (entry != null && playerUuid.equals(entry.playerUuid())) store.remove(token, entry);
    }

    private static void purgeExpired(long nowMs) {
        store.entrySet().removeIf(e -> nowMs > e.getValue().expiryMs());
    }
}
