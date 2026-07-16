package io.github.liquidcatmofu.abs.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class TransferConcurrencyLimiter {
    private final int globalLimit;
    private final int playerLimit;
    private final Map<UUID, Integer> byPlayer = new HashMap<>();
    private int active;

    TransferConcurrencyLimiter(int globalLimit, int playerLimit) {
        if (globalLimit < 1 || playerLimit < 1) throw new IllegalArgumentException("Transfer limits must be positive");
        this.globalLimit = globalLimit;
        this.playerLimit = playerLimit;
    }

    synchronized boolean tryAcquire(UUID playerUuid) {
        if (playerUuid == null || active >= globalLimit || byPlayer.getOrDefault(playerUuid, 0) >= playerLimit) {
            return false;
        }
        active++;
        byPlayer.merge(playerUuid, 1, Integer::sum);
        return true;
    }

    synchronized void release(UUID playerUuid) {
        Integer count = byPlayer.get(playerUuid);
        if (count == null) return;
        active--;
        if (count == 1) byPlayer.remove(playerUuid);
        else byPlayer.put(playerUuid, count - 1);
    }

    synchronized void clear() {
        active = 0;
        byPlayer.clear();
    }
}
