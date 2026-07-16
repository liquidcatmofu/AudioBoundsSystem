package io.github.liquidcatmofu.abs.audio;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ExternalProcessRunner {
    private ExternalProcessRunner() {}

    static int await(Process process, long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        try {
            if (!process.waitFor(timeout, unit)) {
                terminate(process);
                throw new TimeoutException();
            }
            return process.exitValue();
        } catch (InterruptedException e) {
            terminate(process);
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private static void terminate(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
