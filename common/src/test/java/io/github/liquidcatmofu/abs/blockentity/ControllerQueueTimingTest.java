package io.github.liquidcatmofu.abs.blockentity;

import io.github.liquidcatmofu.abs.data.RedstoneMode;
import org.junit.jupiter.api.Test;

import static io.github.liquidcatmofu.abs.blockentity.ControllerQueueTiming.Completion.RESTART;
import static io.github.liquidcatmofu.abs.blockentity.ControllerQueueTiming.Completion.STOP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerQueueTimingTest {
    @Test
    void schedulesNextEntryAfterTrackAndPostTrackDelay() {
        assertEquals(135L, ControllerQueueTiming.afterTrack(100L, 20, 15));
        assertEquals(101L, ControllerQueueTiming.afterTrack(100L, 0, -5));
    }

    @Test
    void entryBecomesDueAtItsScheduledTick() {
        assertFalse(ControllerQueueTiming.isDue(-1L, 100L));
        assertFalse(ControllerQueueTiming.isDue(101L, 100L));
        assertTrue(ControllerQueueTiming.isDue(101L, 101L));
        assertTrue(ControllerQueueTiming.isDue(101L, 102L));
    }

    @Test
    void levelQueueRestartsOnlyWhilePoweredAfterPlayableAudio() {
        assertEquals(RESTART, ControllerQueueTiming.onQueueExhausted(RedstoneMode.LEVEL, 7, true));
        assertEquals(STOP, ControllerQueueTiming.onQueueExhausted(RedstoneMode.LEVEL, 0, true));
        assertEquals(STOP, ControllerQueueTiming.onQueueExhausted(RedstoneMode.LEVEL, 7, false));
        assertEquals(STOP, ControllerQueueTiming.onQueueExhausted(RedstoneMode.PULSE, 7, true));
    }

    @Test
    void levelLoopRestartIsDeferredByOneTick() {
        assertEquals(501L, ControllerQueueTiming.restartOnNextTick(500L));
    }
}
