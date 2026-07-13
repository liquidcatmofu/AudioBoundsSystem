package io.github.liquidcatmofu.abs.library;

import java.nio.file.Path;
import java.util.Optional;

/**
 * ライブラリ参照（"lib:&lt;folderId&gt;/&lt;type&gt;/&lt;entryId&gt;"）を abs_cache の Path に解決するユーティリティ。
 * type は "audio" または "tts"。
 */
public final class LibraryRef {
    public static final String PREFIX = "lib:";

    private LibraryRef() {}

    public record ResolvedAudio(Path path, String contentHash) {}

    /**
     * 参照文字列を解決して abs_cache 内の実ファイル Path を返す。
     * 参照が存在しない場合は空の Optional を返す。
     */
    public static Optional<Path> resolve(String ref) {
        return resolveAudio(ref).map(ResolvedAudio::path);
    }

    /** 音声実体と、利用可能ならSHA-256コンテンツハッシュを返す。 */
    public static Optional<ResolvedAudio> resolveAudio(String ref) {
        if (ref == null || ref.isBlank()) return Optional.empty();
        if (ref.startsWith(PREFIX)) {
            String body = ref.substring(PREFIX.length());
            String[] parts = body.split("/", 3);
            if (parts.length != 3) return Optional.empty();
            String folderId = parts[0];
            String type     = parts[1];
            String entryId  = parts[2];
            if (!ABSLibrary.isSafeId(folderId) || !ABSLibrary.isSafeId(entryId)) return Optional.empty();
            return switch (type) {
                case "audio" -> LibraryAudio.load(folderId, entryId)
                        .flatMap(entry -> LibraryAudio.cacheFilePath(entry)
                                .map(path -> new ResolvedAudio(path, entry.contentHash)));
                case "tts"   -> LibraryTts.load(folderId, entryId)
                        .flatMap(entry -> LibraryTts.cacheFilePath(entry)
                                .map(path -> new ResolvedAudio(path, entry.contentHash)));
                default      -> Optional.empty();
            };
        }
        return Optional.empty();
    }

    /** lib: 参照文字列を生成する。 */
    public static String of(String folderId, String type, String entryId) {
        return PREFIX + folderId + "/" + type + "/" + entryId;
    }

    public static boolean isLibRef(String ref) {
        return ref != null && ref.startsWith(PREFIX);
    }

    /** lib: 参照からエントリの displayName を返す。解決できなければ空文字。サーバー側専用。 */
    public static String resolveDisplayName(String ref) {
        if (ref == null || ref.isBlank() || !ref.startsWith(PREFIX)) return "";
        String body = ref.substring(PREFIX.length());
        String[] parts = body.split("/", 3);
        if (parts.length != 3) return "";
        return switch (parts[1]) {
            case "audio"    -> LibraryAudio.load(parts[0], parts[2]).map(e -> e.displayName).orElse("");
            case "tts"      -> LibraryTts.load(parts[0], parts[2]).map(e -> e.displayName).orElse("");
            case "sequence" -> LibrarySequence.load(parts[0], parts[2]).map(e -> e.displayName).orElse("");
            default -> "";
        };
    }
}
