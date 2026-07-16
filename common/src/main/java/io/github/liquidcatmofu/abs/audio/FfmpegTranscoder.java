package io.github.liquidcatmofu.abs.audio;

import io.github.liquidcatmofu.abs.AudioBoundsSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 任意の音声ファイルを Ogg Vorbis に変換するCore共通サービス。
 * Coreのアップロード処理とTTS Addonの双方から利用する。
 */
public final class FfmpegTranscoder {
    private static final long TRANSCODE_TIMEOUT_SECONDS = 90;
    private static final int MAX_DIAGNOSTIC_BYTES = 16 * 1024;
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
        return toOgg(input, inputExt, ffmpegPath);
    }

    /**
     * 入力バイト列を、指定されたffmpeg実行ファイルを使ってOgg Vorbisへ変換する。
     * Addon固有設定などで実行ファイルのパスを明示する場合に使用する。
     *
     * @param input          入力ファイルのバイト列
     * @param inputExt       入力拡張子（"mp3" / "wav" など）
     * @param ffmpegExecutable PATHで解決できる名前、またはffmpegの絶対パス
     */
    public static byte[] toOgg(byte[] input, String inputExt, String ffmpegExecutable)
            throws IOException, InterruptedException {
        String ext = (inputExt == null || inputExt.isBlank()) ? "bin" : inputExt;
        String executable = (ffmpegExecutable == null || ffmpegExecutable.isBlank())
                ? "ffmpeg" : ffmpegExecutable;
        Path tempDir = Files.createTempDirectory("abs-audio-");
        Path inputFile = tempDir.resolve("input." + ext);
        Path outputOgg = tempDir.resolve("output.ogg");
        Path ffmpegLog = tempDir.resolve("ffmpeg.log");
        try {
            Files.write(inputFile, input);
            return transcode(inputFile, outputOgg, ffmpegLog, executable, ext, input.length);
        } finally {
            Files.deleteIfExists(outputOgg);
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(ffmpegLog);
            Files.deleteIfExists(tempDir);
        }
    }

    public static byte[] toOgg(Path inputFile, String inputExt) throws IOException, InterruptedException {
        String ext = (inputExt == null || inputExt.isBlank()) ? "bin" : inputExt;
        Path tempDir = Files.createTempDirectory("abs-audio-");
        Path outputOgg = tempDir.resolve("output.ogg");
        Path ffmpegLog = tempDir.resolve("ffmpeg.log");
        try {
            return transcode(inputFile, outputOgg, ffmpegLog, ffmpegPath, ext, Files.size(inputFile));
        } finally {
            Files.deleteIfExists(outputOgg);
            Files.deleteIfExists(ffmpegLog);
            Files.deleteIfExists(tempDir);
        }
    }

    private static byte[] transcode(Path inputFile, Path outputOgg, Path ffmpegLog,
                                    String executable, String ext, long inputSize)
            throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder(buildCommand(executable, inputFile, outputOgg));
            pb.redirectErrorStream(true);
            pb.redirectOutput(ffmpegLog.toFile());
            Process proc = pb.start();
        int exit;
        try {
            exit = ExternalProcessRunner.await(proc, TRANSCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IOException("ffmpeg timed out after " + TRANSCODE_TIMEOUT_SECONDS + " seconds: "
                    + readDiagnostic(ffmpegLog), e);
        }
            if (exit != 0) {
                throw new IOException("ffmpeg failed (exit=" + exit + "): " + readDiagnostic(ffmpegLog));
            }
            AudioBoundsSystem.LOGGER.info("ABS: transcoded {} bytes ({}) -> {} bytes Ogg",
                    inputSize, ext, Files.size(outputOgg));
            return Files.readAllBytes(outputOgg);
    }

    static List<String> buildCommand(String executable, Path inputFile, Path outputOgg) {
        return List.of(
                executable, "-y",
                "-i", inputFile.toString(),
                "-vn",
                "-c:a", "libvorbis",
                "-q:a", "4",
                // Oggのランダムなstream serialと可変encoder tagを固定し、同一入力のSHA-256を安定させる。
                "-fflags", "+bitexact",
                outputOgg.toString()
        );
    }

    private static String readDiagnostic(Path log) {
        try (var input = Files.newInputStream(log)) {
            return new String(input.readNBytes(MAX_DIAGNOSTIC_BYTES), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(ffmpeg diagnostic unavailable)";
        }
    }
}
