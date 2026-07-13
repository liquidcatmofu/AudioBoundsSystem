package io.github.liquidcatmofu.abs.audio;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FfmpegTranscoderTest {
    @Test
    void requestsBitExactOggMuxingForStableContentHashes() {
        List<String> command = FfmpegTranscoder.buildCommand(
                "ffmpeg-custom", Path.of("input.wav"), Path.of("output.ogg"));

        assertEquals(List.of(
                "ffmpeg-custom", "-y",
                "-i", "input.wav",
                "-vn",
                "-c:a", "libvorbis",
                "-q:a", "4",
                "-fflags", "+bitexact",
                "output.ogg"
        ), command);
    }
}
