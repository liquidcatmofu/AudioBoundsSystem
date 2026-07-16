package io.github.liquidcatmofu.abs.client.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioTransferRetryPolicyTest {
    @Test
    void retriesOnlyRetryableFailuresBeforeFinalAttempt() {
        assertTrue(AudioTransferRetryPolicy.shouldRetry(true, 1));
        assertTrue(AudioTransferRetryPolicy.shouldRetry(true, 2));
        assertFalse(AudioTransferRetryPolicy.shouldRetry(true, 3));
        assertFalse(AudioTransferRetryPolicy.shouldRetry(false, 1));
    }

    @Test
    void appliesBoundedLinearBackoff() {
        assertEquals(200, AudioTransferRetryPolicy.delayMillis(1));
        assertEquals(400, AudioTransferRetryPolicy.delayMillis(2));
    }
}
