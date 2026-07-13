package io.github.liquidcatmofu.abs.server;

import dev.architectury.event.events.common.LifecycleEvent;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.audio.FfmpegSupport;
import io.github.liquidcatmofu.abs.library.ABSLibrary;
import io.github.liquidcatmofu.abs.library.LibraryCacheMaintenance;
import io.github.liquidcatmofu.abs.server.web.WebSessionStore;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;

public class ABSServerLifecycle {
    public static void register() {
        LifecycleEvent.SERVER_STARTING.register(server -> {
            ABSLibrary.init(server.getWorldPath(LevelResource.ROOT));
            FfmpegSupport.runStartupCheck();
            try {
                ABSHttpServer.start(server);
                LibraryCacheMaintenance.start();
            } catch (IOException e) {
                AudioBoundsSystem.LOGGER.error("Failed to start ABS HTTP Server", e);
            }
        });

        LifecycleEvent.SERVER_STOPPING.register(server -> {
            LibraryCacheMaintenance.stop();
            ABSHttpServer.stop();
            FfmpegSupport.stop();
            WebSessionStore.clear();
        });
    }
}
