package io.github.liquidcatmofu.abs.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioTransferServiceLifecycleTest {
    @AfterEach
    void stopService() {
        AudioTransferService.stop();
    }

    @Test
    void startAndStopAreIdempotentAndServiceCanRestart() {
        assertFalse(AudioTransferService.isRunning());

        AudioTransferService.start();
        AudioTransferService.start();
        assertTrue(AudioTransferService.isRunning());

        AudioTransferService.stop();
        AudioTransferService.stop();
        assertFalse(AudioTransferService.isRunning());

        AudioTransferService.start();
        assertTrue(AudioTransferService.isRunning());
    }

    @Test
    void stoppingServiceRevokesOutstandingTransferTokens() {
        AudioTransferService.start();
        UUID player = UUID.randomUUID();
        UUID token = AudioTransferService.generateToken(Path.of("audio.ogg"), player);

        AudioTransferService.stop();

        assertTrue(TokenStore.consume(token, player).isEmpty());
    }
}
