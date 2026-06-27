package io.github.liquidcatmofu.abs.tts.transcode;

import io.github.liquidcatmofu.abs.tts.TTSAddon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FfmpegTranscoder {
    private FfmpegTranscoder() {}

    /**
     * WAV バイト列を Ogg Vorbis に変換して返す。
     * ffmpegPath には PATH 経由で解決できる "ffmpeg" またはフルパスを指定する。
     */
    public static byte[] toOgg(byte[] wavBytes, String ffmpegPath) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("abs-tts-");
        Path inputWav = tempDir.resolve("input.wav");
        Path outputOgg = tempDir.resolve("output.ogg");
        try {
            Files.write(inputWav, wavBytes);

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath, "-y",
                    "-i", inputWav.toString(),
                    "-c:a", "libvorbis",
                    "-q:a", "4",
                    outputOgg.toString()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            byte[] ffmpegOutput = proc.getInputStream().readAllBytes();
            int exitCode = proc.waitFor();

            if (exitCode != 0) {
                throw new IOException("ffmpeg failed (exit=" + exitCode + "): " + new String(ffmpegOutput));
            }
            TTSAddon.LOGGER.info("ABS TTS: ffmpeg transcoded {} bytes WAV → {} bytes Ogg",
                    wavBytes.length, Files.size(outputOgg));
            return Files.readAllBytes(outputOgg);
        } finally {
            Files.deleteIfExists(outputOgg);
            Files.deleteIfExists(inputWav);
            Files.deleteIfExists(tempDir);
        }
    }
}
