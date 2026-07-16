package io.github.liquidcatmofu.abs.audio;

import io.github.liquidcatmofu.abs.AudioBoundsSystem;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 起動時に ffmpeg / ffprobe の有無と必要なエンコーダ・デコーダの有無を検査し、
 * 不足があれば警告ログを出す。検査は非同期で行い、ワールド読み込みを遅延させない。
 */
public final class FfmpegSupport {
    private static final String FFMPEG  = "ffmpeg";
    private static final String FFPROBE = "ffprobe";

    /** 出力には libvorbis を使用 */
    private static final String REQUIRED_ENCODER = "libvorbis";
    /** よく使う入力形式のデコーダ */
    private static final String[] EXPECTED_DECODERS = { "vorbis", "opus", "flac", "mp3", "aac" };

    private static volatile boolean ffmpegAvailable = false;
    private static volatile boolean vorbisEncoderAvailable = false;
    private static ExecutorService executor;
    private static CompletableFuture<Void> startupTask;

    private FfmpegSupport() {}

    public static boolean isFfmpegAvailable() {
        return ffmpegAvailable;
    }

    public static boolean isVorbisEncoderAvailable() {
        return vorbisEncoderAvailable;
    }

    /** 非同期で検査を開始する。 */
    public static synchronized void runStartupCheck() {
        if (startupTask != null && !startupTask.isDone()) {
            return;
        }
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "abs-ffmpeg-probe");
                thread.setDaemon(true);
                return thread;
            });
        }
        startupTask = CompletableFuture.runAsync(FfmpegSupport::check, executor);
    }

    /** サーバー停止時に進行中の機能検査をキャンセルし、専用 Executor を終了する。 */
    public static synchronized void stop() {
        if (startupTask != null) {
            startupTask.cancel(true);
            startupTask = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static void check() {
        // ── ffmpeg 本体 ──
        if (!probeVersion(FFMPEG)) {
            ffmpegAvailable = false;
            AudioBoundsSystem.LOGGER.warn(
                "ABS: 'ffmpeg' が見つかりません（PATH を確認してください）。音声のアップロード・変換は利用できません。");
            return;
        }
        ffmpegAvailable = true;
        AudioBoundsSystem.LOGGER.info("ABS: ffmpeg を検出しました。");

        // ── ffprobe（現状未使用だが将来用に検査） ──
        if (probeVersion(FFPROBE)) {
            AudioBoundsSystem.LOGGER.info("ABS: ffprobe を検出しました。");
        } else {
            AudioBoundsSystem.LOGGER.warn(
                "ABS: 'ffprobe' が見つかりません（現状は未使用ですが、将来の機能で必要になる場合があります）。");
        }

        // ── エンコーダ ──
        String encoders = capture(FFMPEG, "-hide_banner", "-encoders");
        vorbisEncoderAvailable = encoders != null && encoders.contains(REQUIRED_ENCODER);
        if (!vorbisEncoderAvailable) {
            AudioBoundsSystem.LOGGER.warn(
                "ABS: ffmpeg に '{}' エンコーダがありません。ogg への変換に失敗します。", REQUIRED_ENCODER);
        }

        // ── デコーダ ──
        String decoders = capture(FFMPEG, "-hide_banner", "-decoders");
        if (decoders != null) {
            List<String> missing = new ArrayList<>();
            for (String codec : EXPECTED_DECODERS) {
                if (!decoders.contains(codec)) missing.add(codec);
            }
            if (!missing.isEmpty()) {
                AudioBoundsSystem.LOGGER.warn(
                    "ABS: ffmpeg に不足しているデコーダ: {}（該当形式のアップロードは変換できません）。", missing);
            }
        }

        AudioBoundsSystem.LOGGER.info(
            "ABS: ffmpeg 機能チェック完了（libvorbis エンコーダ: {}）。",
            vorbisEncoderAvailable ? "OK" : "なし");
    }

    /** `<bin> -version` が起動できれば true。 */
    private static boolean probeVersion(String bin) {
        return capture(bin, "-hide_banner", "-version") != null;
    }

    /** コマンドを実行し標準出力（+標準エラー）を返す。起動失敗・タイムアウト時は null。 */
    private static String capture(String... cmd) {
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("abs-ffmpeg-probe-", ".log");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(output.toFile());
            process = pb.start();
            ExternalProcessRunner.await(process, 15, TimeUnit.SECONDS);
            try (var input = Files.newInputStream(output)) {
                return new String(input.readNBytes(4 * 1024 * 1024), StandardCharsets.UTF_8);
            }
        } catch (TimeoutException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (Exception ignored) {}
            }
        }
    }
}
