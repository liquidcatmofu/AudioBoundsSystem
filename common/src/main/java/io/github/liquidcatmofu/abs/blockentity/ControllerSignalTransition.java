package io.github.liquidcatmofu.abs.blockentity;

import io.github.liquidcatmofu.abs.data.ControllerRetriggerMode;
import io.github.liquidcatmofu.abs.data.RedstoneMode;

/** Pure transition rules for controller redstone input and active-playback retriggers. */
final class ControllerSignalTransition {
    enum Action {
        NONE,
        START,
        STOP,
        RESTART
    }

    private ControllerSignalTransition() {}

    static Action onSignalChange(RedstoneMode mode, int previousSignal, int currentSignal) {
        int previous = clampSignal(previousSignal);
        int current = clampSignal(currentSignal);

        if (mode == RedstoneMode.LEVEL) {
            if (current == 0) {
                return previous > 0 ? Action.STOP : Action.NONE;
            }
            return previous != current ? Action.START : Action.NONE;
        }

        return previous == 0 && current > 0 ? Action.START : Action.NONE;
    }

    static Action onTrigger(boolean playbackActive, ControllerRetriggerMode mode) {
        if (!playbackActive) {
            return Action.START;
        }
        return mode == ControllerRetriggerMode.STOP ? Action.STOP : Action.RESTART;
    }

    static int clampSignal(int signal) {
        return Math.max(0, Math.min(15, signal));
    }
}
