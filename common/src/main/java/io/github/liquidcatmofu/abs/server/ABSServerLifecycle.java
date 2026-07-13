package io.github.liquidcatmofu.abs.server;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import io.github.liquidcatmofu.abs.audio.FfmpegSupport;
import io.github.liquidcatmofu.abs.library.ABSLibrary;
import io.github.liquidcatmofu.abs.library.LibraryCacheMaintenance;
import io.github.liquidcatmofu.abs.server.web.WebSessionStore;
import io.github.liquidcatmofu.abs.server.web.WebRpcService;
import net.minecraft.world.level.storage.LevelResource;

public class ABSServerLifecycle {
    public static void register() {
        PlayerEvent.PLAYER_QUIT.register(WebRpcService::playerDisconnected);

        LifecycleEvent.SERVER_STARTING.register(server -> {
            var worldRoot = server.getWorldPath(LevelResource.ROOT);
            ABSLibrary.init(worldRoot);
            try {
                ServerAudioCache.init(worldRoot);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Failed to initialize ABS audio cache", e);
            }
            FfmpegSupport.runStartupCheck();
            AudioTransferService.start();
            WebRpcService.start(server);
            LibraryCacheMaintenance.start();
        });

        LifecycleEvent.SERVER_STOPPING.register(server -> {
            LibraryCacheMaintenance.stop();
            AudioTransferService.stop();
            WebRpcService.stop();
            FfmpegSupport.stop();
            WebSessionStore.clear();
        });
    }
}
