package io.github.liquidcatmofu.abs.client.audio;

import io.github.liquidcatmofu.abs.audio.AudioContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAudioCacheTest {
    @TempDir Path tempDir;

    @Test
    void storesAndReloadsVerifiedAudio() throws Exception {
        byte[] bytes = ogg("one");
        String hash = AudioContent.sha256(bytes);
        ClientAudioCache cache = new ClientAudioCache(tempDir, 1024);

        cache.put(hash, bytes);

        assertArrayEquals(bytes, cache.get(hash).orElseThrow());
    }

    @Test
    void deletesCorruptedCachedAudio() throws Exception {
        byte[] bytes = ogg("valid");
        String hash = AudioContent.sha256(bytes);
        ClientAudioCache cache = new ClientAudioCache(tempDir, 1024);
        cache.put(hash, bytes);
        Files.write(tempDir.resolve(hash + ".ogg"), ogg("corrupt"));

        assertTrue(cache.get(hash).isEmpty());
        assertFalse(Files.exists(tempDir.resolve(hash + ".ogg")));
    }

    @Test
    void evictsTheLeastRecentlyAccessedFile() throws Exception {
        byte[] first = ogg("11111");
        byte[] second = ogg("22222");
        byte[] third = ogg("33333");
        String firstHash = AudioContent.sha256(first);
        String secondHash = AudioContent.sha256(second);
        String thirdHash = AudioContent.sha256(third);
        ClientAudioCache cache = new ClientAudioCache(tempDir, first.length + second.length);
        cache.put(firstHash, first);
        cache.put(secondHash, second);
        Files.setLastModifiedTime(tempDir.resolve(firstHash + ".ogg"), FileTime.fromMillis(1));
        Files.setLastModifiedTime(tempDir.resolve(secondHash + ".ogg"), FileTime.fromMillis(2));

        cache.put(thirdHash, third);

        assertFalse(Files.exists(tempDir.resolve(firstHash + ".ogg")));
        assertTrue(Files.exists(tempDir.resolve(thirdHash + ".ogg")));
    }

    private static byte[] ogg(String value) {
        return ("OggS-" + value).getBytes(StandardCharsets.UTF_8);
    }
}
