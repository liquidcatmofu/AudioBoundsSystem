package io.github.liquidcatmofu.abs.blockentity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubtitleDisplayTextTest {
    @Test
    void configuredTitleTakesPriority() {
        assertEquals("Configured title",
                SubtitleDisplayText.trackTitle("  Configured title  ", "Audio name"));
    }

    @Test
    void audioDisplayNameIsUsedWhenTitleIsBlank() {
        assertEquals("Audio name", SubtitleDisplayText.trackTitle(" ", "  Audio name  "));
    }

    @Test
    void missingDisplayTextStaysEmpty() {
        assertEquals("", SubtitleDisplayText.trackTitle(null, " "));
    }
}
