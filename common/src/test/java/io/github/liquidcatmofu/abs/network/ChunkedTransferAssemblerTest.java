package io.github.liquidcatmofu.abs.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkedTransferAssemblerTest {
    @Test
    void assemblesOrderedChunksAtTheConfiguredLimit() throws Exception {
        ChunkedTransferAssembler assembler = new ChunkedTransferAssembler(5);

        assertNull(assembler.accept(5, 0, new byte[]{1, 2}));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5},
                assembler.accept(5, 2, new byte[]{3, 4, 5}));
    }

    @Test
    void rejectsAnOversizedTransferBeforeAllocation() {
        assertThrows(IOException.class,
                () -> new ChunkedTransferAssembler(5).accept(6, 0, new byte[]{1}));
    }

    @Test
    void rejectsOutOfOrderOrOverflowingChunks() throws Exception {
        ChunkedTransferAssembler outOfOrder = new ChunkedTransferAssembler(5);
        assertThrows(IOException.class, () -> outOfOrder.accept(5, 1, new byte[]{1}));

        ChunkedTransferAssembler overflow = new ChunkedTransferAssembler(5);
        assertNull(overflow.accept(5, 0, new byte[]{1, 2, 3, 4}));
        assertThrows(IOException.class, () -> overflow.accept(5, 4, new byte[]{5, 6}));
    }

    @Test
    void rejectsAChangedDeclaredLength() throws Exception {
        ChunkedTransferAssembler inconsistent = new ChunkedTransferAssembler(5);
        assertNull(inconsistent.accept(5, 0, new byte[]{1}));
        assertThrows(IOException.class, () -> inconsistent.accept(4, 1, new byte[]{2}));
    }
}
