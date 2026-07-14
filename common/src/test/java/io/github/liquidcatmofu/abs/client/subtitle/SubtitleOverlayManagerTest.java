package io.github.liquidcatmofu.abs.client.subtitle;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtitleOverlayManagerTest {
    private final SubtitleOverlayManager overlay = SubtitleOverlayManager.INSTANCE;

    @AfterEach
    void clearOverlay() {
        overlay.clear();
    }

    @Test
    void trimsTextAndShowsForAtLeastTwentyTicks() {
        overlay.show(BlockPos.ZERO, "  Track  ", "  Subtitle  ", 1);

        assertTrue(overlay.isVisible());
        assertEquals("Track", overlay.getTrackTitle());
        assertEquals("Subtitle", overlay.getSubtitle());

        tick(19);
        assertTrue(overlay.isVisible());
        overlay.tick();
        assertFalse(overlay.isVisible());
    }

    @Test
    void fadesDuringFinalThirdOfShortOverlay() {
        overlay.show(BlockPos.ZERO, "Track", "", 20);

        tick(14);
        assertEquals(1.0F, overlay.alpha());
        overlay.tick();
        assertEquals(5.0F / 6.0F, overlay.alpha());
        tick(5);
        assertEquals(0.0F, overlay.alpha());
    }

    @Test
    void clearFromDifferentSourceDoesNotHideOverlay() {
        BlockPos source = new BlockPos(1, 2, 3);
        overlay.show(source, "Track", "Subtitle", 100);

        overlay.clear(new BlockPos(4, 5, 6));
        assertTrue(overlay.isVisible());

        overlay.clear(source);
        assertFalse(overlay.isVisible());
    }

    @Test
    void newerOverlayReplacesPreviousAndBlankTextStaysHidden() {
        overlay.show(BlockPos.ZERO, "First", "Old", 100);
        overlay.show(BlockPos.ZERO, null, " New ", 20);

        assertEquals("", overlay.getTrackTitle());
        assertEquals("New", overlay.getSubtitle());
        assertTrue(overlay.isVisible());

        overlay.show(BlockPos.ZERO, " ", null, 20);
        assertFalse(overlay.isVisible());
    }

    private void tick(int count) {
        for (int i = 0; i < count; i++) overlay.tick();
    }
}
