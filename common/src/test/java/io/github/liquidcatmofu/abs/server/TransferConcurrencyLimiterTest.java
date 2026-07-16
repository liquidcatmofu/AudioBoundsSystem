package io.github.liquidcatmofu.abs.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferConcurrencyLimiterTest {
    @Test
    void enforcesPerPlayerLimitWithoutBlockingAnotherPlayer() {
        TransferConcurrencyLimiter limiter = new TransferConcurrencyLimiter(3, 2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(first));
        assertTrue(limiter.tryAcquire(first));
        assertFalse(limiter.tryAcquire(first));
        assertTrue(limiter.tryAcquire(second));
    }

    @Test
    void enforcesGlobalLimitAndReleasesCapacity() {
        TransferConcurrencyLimiter limiter = new TransferConcurrencyLimiter(2, 2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(first));
        assertTrue(limiter.tryAcquire(second));
        assertFalse(limiter.tryAcquire(UUID.randomUUID()));

        limiter.release(first);
        assertTrue(limiter.tryAcquire(UUID.randomUUID()));
    }

    @Test
    void unmatchedReleaseDoesNotCreateCapacity() {
        TransferConcurrencyLimiter limiter = new TransferConcurrencyLimiter(1, 1);
        UUID active = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(active));
        limiter.release(UUID.randomUUID());

        assertFalse(limiter.tryAcquire(UUID.randomUUID()));
    }
}
