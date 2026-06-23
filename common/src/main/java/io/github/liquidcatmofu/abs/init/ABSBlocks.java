package io.github.liquidcatmofu.abs.init;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.block.AudioControllerBlock;
import io.github.liquidcatmofu.abs.block.SpeakerBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ABSBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(AudioBoundsSystem.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<Block> SPEAKER =
            BLOCKS.register("speaker", () -> new SpeakerBlock(BlockBehaviour.Properties.of()));

    public static final RegistrySupplier<Block> AUDIO_CONTROLLER =
            BLOCKS.register("audio_controller", () -> new AudioControllerBlock(BlockBehaviour.Properties.of()));

    public static void register() {
        BLOCKS.register();
    }
}
