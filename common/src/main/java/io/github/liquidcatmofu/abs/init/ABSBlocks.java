package io.github.liquidcatmofu.abs.init;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.block.AudioControllerBlock;
import io.github.liquidcatmofu.abs.block.SpeakerBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

public class ABSBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(AudioBoundsSystem.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<Block> SPEAKER =
            BLOCKS.register("speaker", SpeakerBlock::new);

    public static final RegistrySupplier<Block> AUDIO_CONTROLLER =
            BLOCKS.register("audio_controller", AudioControllerBlock::new);

    public static void register() {
        BLOCKS.register();
    }
}
