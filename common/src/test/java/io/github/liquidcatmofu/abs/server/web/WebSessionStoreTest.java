package io.github.liquidcatmofu.abs.server.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSessionStoreTest {
    private static final long CREATED_AT = 10_000L;

    @AfterEach
    void clearSessions() {
        WebSessionStore.clear();
    }

    @Test
    void sessionResolvesToItsPlayerUntilEightHourBoundary() {
        UUID player = UUID.randomUUID();
        UUID session = WebSessionStore.createSession(player, CREATED_AT);

        assertEquals(Optional.of(player), WebSessionStore.getPlayerUuid(session, CREATED_AT));
        assertEquals(Optional.of(player),
                WebSessionStore.getPlayerUuid(session, CREATED_AT + WebSessionStore.TTL_MS));
    }

    @Test
    void sessionIsRejectedAndRemovedAfterExpiry() {
        UUID player = UUID.randomUUID();
        UUID session = WebSessionStore.createSession(player, CREATED_AT);

        assertEquals(Optional.empty(),
                WebSessionStore.getPlayerUuid(session, CREATED_AT + WebSessionStore.TTL_MS + 1));
        assertEquals(Optional.empty(), WebSessionStore.getPlayerUuid(session, CREATED_AT));
    }

    @Test
    void invalidateAndClearRevokeSessions() {
        UUID first = WebSessionStore.createSession(UUID.randomUUID(), CREATED_AT);
        UUID second = WebSessionStore.createSession(UUID.randomUUID(), CREATED_AT);

        WebSessionStore.invalidate(first);
        assertEquals(Optional.empty(), WebSessionStore.getPlayerUuid(first, CREATED_AT));

        WebSessionStore.clear();
        assertEquals(Optional.empty(), WebSessionStore.getPlayerUuid(second, CREATED_AT));
    }

    @Test
    void nullAndUnknownSessionsAreRejected() {
        assertEquals(Optional.empty(), WebSessionStore.getPlayerUuid(null, CREATED_AT));
        assertEquals(Optional.empty(), WebSessionStore.getPlayerUuid(UUID.randomUUID(), CREATED_AT));
        WebSessionStore.invalidate(null);
    }
}
