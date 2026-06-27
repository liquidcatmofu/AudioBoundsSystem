package io.github.liquidcatmofu.abs.server;

import dev.architectury.event.events.common.LifecycleEvent;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.library.ABSLibrary;
import io.github.liquidcatmofu.abs.server.web.WebSessionStore;

import java.io.IOException;

public class ABSServerLifecycle {
    public static void register() {
        LifecycleEvent.SERVER_STARTING.register(server -> {
            ABSLibrary.init(server.getServerDirectory().toPath());
            try {
                ABSHttpServer.start(server);
            } catch (IOException e) {
                AudioBoundsSystem.LOGGER.error("Failed to start ABS HTTP Server", e);
            }
        });

        LifecycleEvent.SERVER_STOPPING.register(server -> {
            ABSHttpServer.stop();
            WebSessionStore.clear();
        });
    }
}
