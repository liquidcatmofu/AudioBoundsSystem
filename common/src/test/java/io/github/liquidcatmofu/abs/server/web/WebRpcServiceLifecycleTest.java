package io.github.liquidcatmofu.abs.server.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebRpcServiceLifecycleTest {
    @AfterEach
    void stopService() {
        WebRpcService.stop();
    }

    @Test
    void startAndStopAreIdempotentAndRestartable() {
        WebRpcService.start(null);
        WebRpcService.start(null);
        assertTrue(WebRpcService.isRunning());

        WebRpcService.stop();
        WebRpcService.stop();
        assertFalse(WebRpcService.isRunning());

        WebRpcService.start(null);
        assertTrue(WebRpcService.isRunning());
    }
}
