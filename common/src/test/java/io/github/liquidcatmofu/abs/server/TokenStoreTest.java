package io.github.liquidcatmofu.abs.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenStoreTest {
    @AfterEach
    void clearTokens() {
        TokenStore.clear();
    }

    @Test
    void tokenCanBeConsumedOnlyOnce() {
        Path audio = Path.of("audio.ogg");
        UUID token = TokenStore.generate(audio, 1_000L);

        assertEquals(Optional.of(audio), TokenStore.consume(token, 1_001L));
        assertEquals(Optional.empty(), TokenStore.consume(token, 1_002L));
    }

    @Test
    void tokenExpiresAfterSixtySeconds() {
        Path audio = Path.of("audio.ogg");
        UUID validAtBoundary = TokenStore.generate(audio, 1_000L);
        UUID expired = TokenStore.generate(audio, 1_000L);

        assertEquals(Optional.of(audio), TokenStore.consume(validAtBoundary, 1_000L + TokenStore.EXPIRY_MS));
        assertEquals(Optional.empty(), TokenStore.consume(expired, 1_001L + TokenStore.EXPIRY_MS));
    }

    @Test
    void clearRevokesOutstandingTokens() {
        UUID token = TokenStore.generate(Path.of("audio.ogg"), 1_000L);

        TokenStore.clear();

        assertEquals(Optional.empty(), TokenStore.consume(token, 1_001L));
        assertEquals(Optional.empty(), TokenStore.consume(null, 1_001L));
    }

    @Test
    void concurrentConsumersCannotBothClaimToken() throws Exception {
        UUID token = TokenStore.generate(Path.of("audio.ogg"), 1_000L);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<Path>> first = executor.submit(() -> {
                start.await();
                return TokenStore.consume(token, 1_001L);
            });
            Future<Optional<Path>> second = executor.submit(() -> {
                start.await();
                return TokenStore.consume(token, 1_001L);
            });

            start.countDown();
            long successfulConsumers = java.util.stream.Stream.of(first.get(), second.get())
                    .filter(Optional::isPresent)
                    .count();
            assertEquals(1L, successfulConsumers);
            assertTrue(first.get().isEmpty() || second.get().isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }
}
