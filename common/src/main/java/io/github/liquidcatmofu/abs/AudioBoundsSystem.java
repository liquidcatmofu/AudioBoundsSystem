package io.github.liquidcatmofu.abs;

import io.github.liquidcatmofu.abs.init.ABSBlockEntities;
import io.github.liquidcatmofu.abs.init.ABSBlocks;
import io.github.liquidcatmofu.abs.init.ABSCreativeTabs;
import io.github.liquidcatmofu.abs.init.ABSItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AudioBoundsSystem {
    public static final String MOD_ID = "abs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        ABSBlocks.register();
        ABSItems.register();
        ABSBlockEntities.register();
        ABSCreativeTabs.register();
    }
}
