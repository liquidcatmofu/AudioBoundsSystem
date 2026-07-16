package io.github.liquidcatmofu.abs.client.audio;

final class AudioTransferRetryPolicy {
    static final int MAX_ATTEMPTS = 3;

    private AudioTransferRetryPolicy() {}

    static boolean shouldRetry(boolean retryable, int attempt) {
        return retryable && attempt >= 1 && attempt < MAX_ATTEMPTS;
    }

    static long delayMillis(int attempt) {
        return 200L * Math.max(1, attempt);
    }
}
