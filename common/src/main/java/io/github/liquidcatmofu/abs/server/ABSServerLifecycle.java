package io.github.liquidcatmofu.abs.server;

import dev.architectury.event.events.common.LifecycleEvent;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;

import java.io.IOException;

public class ABSServerLifecycle {
    public static void register() {
        LifecycleEvent.SERVER_STARTING.register(server -> {
            try {
                ABSHttpServer.start(server);
            } catch (IOException e) {
                AudioBoundsSystem.LOGGER.error("Failed to start ABS HTTP Server", e);
            }
        });

        LifecycleEvent.SERVER_STOPPING.register(server -> ABSHttpServer.stop());
    }
}
