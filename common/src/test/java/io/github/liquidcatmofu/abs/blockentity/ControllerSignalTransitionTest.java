package io.github.liquidcatmofu.abs.blockentity;

import io.github.liquidcatmofu.abs.data.ControllerRetriggerMode;
import io.github.liquidcatmofu.abs.data.RedstoneMode;
import org.junit.jupiter.api.Test;

import static io.github.liquidcatmofu.abs.blockentity.ControllerSignalTransition.Action.NONE;
import static io.github.liquidcatmofu.abs.blockentity.ControllerSignalTransition.Action.RESTART;
import static io.github.liquidcatmofu.abs.blockentity.ControllerSignalTransition.Action.START;
import static io.github.liquidcatmofu.abs.blockentity.ControllerSignalTransition.Action.STOP;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerSignalTransitionTest {
    @Test
    void pulseTriggersOnlyOnRisingEdge() {
        assertEquals(START, ControllerSignalTransition.onSignalChange(RedstoneMode.PULSE, 0, 7));
        assertEquals(NONE, ControllerSignalTransition.onSignalChange(RedstoneMode.PULSE, 7, 12));
        assertEquals(NONE, ControllerSignalTransition.onSignalChange(RedstoneMode.PULSE, 12, 0));
        assertEquals(START, ControllerSignalTransition.onSignalChange(RedstoneMode.PULSE, 0, 12));
    }

    @Test
    void levelTracksSignalChangesAndStopsAtZero() {
        assertEquals(START, ControllerSignalTransition.onSignalChange(RedstoneMode.LEVEL, 0, 4));
        assertEquals(NONE, ControllerSignalTransition.onSignalChange(RedstoneMode.LEVEL, 4, 4));
        assertEquals(START, ControllerSignalTransition.onSignalChange(RedstoneMode.LEVEL, 4, 9));
        assertEquals(STOP, ControllerSignalTransition.onSignalChange(RedstoneMode.LEVEL, 9, 0));
        assertEquals(NONE, ControllerSignalTransition.onSignalChange(RedstoneMode.LEVEL, 0, 0));
    }

    @Test
    void signalsAreClampedBeforeTransitionsAreCompared() {
        assertEquals(START, ControllerSignalTransition.onSignalChange(RedstoneMode.LEVEL, -1, 20));
        assertEquals(NONE, ControllerSignalTransition.onSignalChange(RedstoneMode.LEVEL, 20, 15));
        assertEquals(STOP, ControllerSignalTransition.onSignalChange(RedstoneMode.LEVEL, 20, -1));
    }

    @Test
    void retriggerModeControlsActivePlayback() {
        assertEquals(START, ControllerSignalTransition.onTrigger(false, ControllerRetriggerMode.STOP));
        assertEquals(START, ControllerSignalTransition.onTrigger(false, ControllerRetriggerMode.RESTART));
        assertEquals(STOP, ControllerSignalTransition.onTrigger(true, ControllerRetriggerMode.STOP));
        assertEquals(RESTART, ControllerSignalTransition.onTrigger(true, ControllerRetriggerMode.RESTART));
    }
}
