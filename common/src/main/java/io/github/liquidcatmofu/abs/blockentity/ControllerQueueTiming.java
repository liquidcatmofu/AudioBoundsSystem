package io.github.liquidcatmofu.abs.blockentity;

import io.github.liquidcatmofu.abs.data.RedstoneMode;

/** Pure timing and completion rules for controller queue playback. */
final class ControllerQueueTiming {
    enum Completion {
        STOP,
        RESTART
    }

    private ControllerQueueTiming() {}

    static long afterTrack(long currentTick, int durationTicks, int delayAfterTicks) {
        return currentTick + Math.max(1, durationTicks) + Math.max(0, delayAfterTicks);
    }

    static boolean isDue(long nextTrackTick, long currentTick) {
        return nextTrackTick >= 0 && currentTick >= nextTrackTick;
    }

    static Completion onQueueExhausted(RedstoneMode mode, int signal, boolean hadPlayableTrack) {
        return mode == RedstoneMode.LEVEL && signal > 0 && hadPlayableTrack
                ? Completion.RESTART
                : Completion.STOP;
    }

    static long restartOnNextTick(long currentTick) {
        return currentTick + 1L;
    }
}
