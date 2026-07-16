package io.github.liquidcatmofu.abs.audio;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalProcessRunnerTest {
    @Test
    void returnsCompletedProcessExitCode() throws Exception {
        FakeProcess process = new FakeProcess(7, true);

        assertEquals(7, ExternalProcessRunner.await(process, 90, TimeUnit.SECONDS));
        assertEquals(0, process.destroyCalls);
    }

    @Test
    void timeoutEscalatesFromGracefulToForcedTermination() {
        FakeProcess process = new FakeProcess(0, false, false, true);

        assertThrows(TimeoutException.class,
                () -> ExternalProcessRunner.await(process, 90, TimeUnit.SECONDS));
        assertEquals(1, process.destroyCalls);
        assertEquals(1, process.forceCalls);
    }

    @Test
    void interruptionTerminatesProcessAndPreservesInterruptFlag() {
        FakeProcess process = new FakeProcess(0, true);
        process.interruptFirstWait = true;
        try {
            assertThrows(InterruptedException.class,
                    () -> ExternalProcessRunner.await(process, 90, TimeUnit.SECONDS));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, process.destroyCalls);
        } finally {
            Thread.interrupted();
        }
    }

    private static final class FakeProcess extends Process {
        private final int exitCode;
        private final Queue<Boolean> waits = new ArrayDeque<>();
        private boolean interruptFirstWait;
        private int waitCalls;
        private int destroyCalls;
        private int forceCalls;

        private FakeProcess(int exitCode, boolean... waits) {
            this.exitCode = exitCode;
            for (boolean wait : waits) this.waits.add(wait);
        }

        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (interruptFirstWait && waitCalls++ == 0) throw new InterruptedException();
            waitCalls++;
            return waits.isEmpty() || waits.remove();
        }
        @Override public int waitFor() { return exitCode; }
        @Override public int exitValue() { return exitCode; }
        @Override public void destroy() { destroyCalls++; }
        @Override public Process destroyForcibly() { forceCalls++; return this; }
        @Override public boolean isAlive() { return false; }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
    }
}
