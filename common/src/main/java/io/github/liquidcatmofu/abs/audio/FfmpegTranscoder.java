package io.github.liquidcatmofu.abs.audio;

import io.github.liquidcatmofu.abs.AudioBoundsSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 任意の音声ファイルを Ogg Vorbis に変換する。ffmpeg は PATH 上にある前提。 */
public final class FfmpegTranscoder {
    private static volatile String ffmpegPath = "ffmpeg";

    private FfmpegTranscoder() {}

    public static void setFfmpegPath(String path) {
        if (path != null && !path.isBlank()) ffmpegPath = path;
    }

    /**
     * 入力バイト列を Ogg Vorbis に変換して返す。
     * @param input    入力ファイルのバイト列
     * @param inputExt 入力拡張子（"mp3" / "wav" など。ffmpeg のフォーマット判定補助）
     */
    public static byte[] toOgg(byte[] input, String inputExt) throws IOException, InterruptedException {
        String ext = (inputExt == null || inputExt.isBlank()) ? "bin" : inputExt;
        Path tempDir = Files.createTempDirectory("abs-audio-");
        Path inputFile = tempDir.resolve("input." + ext);
        Path outputOgg = tempDir.resolve("output.ogg");
        try {
            Files.write(inputFile, input);

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath, "-y",
                    "-i", inputFile.toString(),
                    "-vn",
                    "-c:a", "libvorbis",
                    "-q:a", "4",
                    outputOgg.toString()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            byte[] log = proc.getInputStream().readAllBytes();
            int exit = proc.waitFor();
            if (exit != 0) {
                throw new IOException("ffmpeg failed (exit=" + exit + "): " + new String(log));
            }
            AudioBoundsSystem.LOGGER.info("ABS: transcoded {} bytes ({}) -> {} bytes Ogg",
                    input.length, ext, Files.size(outputOgg));
            return Files.readAllBytes(outputOgg);
        } finally {
            Files.deleteIfExists(outputOgg);
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(tempDir);
        }
    }
}
