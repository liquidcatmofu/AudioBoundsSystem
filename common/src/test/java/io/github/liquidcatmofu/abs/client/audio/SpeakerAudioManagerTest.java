package io.github.liquidcatmofu.abs.client.audio;

import io.github.liquidcatmofu.abs.server.ABSHttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeakerAudioManagerTest {
    private static final UUID TOKEN = UUID.fromString("12345678-1234-5678-9abc-def012345678");

    @Test
    void usesTheConnectedDedicatedServerHost() {
        var remote = InetSocketAddress.createUnresolved("minecraft.example.test", 25565);

        assertEquals("http://minecraft.example.test:" + ABSHttpServer.DEFAULT_PORT + "/audio/" + TOKEN,
                SpeakerAudioManager.audioUrl(TOKEN, remote));
    }

    @Test
    void bracketsIpv6Hosts() {
        var remote = InetSocketAddress.createUnresolved("2001:db8::1", 25565);

        assertEquals("http://[2001:db8::1]:" + ABSHttpServer.DEFAULT_PORT + "/audio/" + TOKEN,
                SpeakerAudioManager.audioUrl(TOKEN, remote));
    }

    @Test
    void fallsBackToLocalhostForAnIntegratedServerConnection() {
        assertEquals("http://localhost:" + ABSHttpServer.DEFAULT_PORT + "/audio/" + TOKEN,
                SpeakerAudioManager.audioUrl(TOKEN, null));
    }
}
