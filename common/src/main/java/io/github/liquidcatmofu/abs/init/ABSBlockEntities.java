package io.github.liquidcatmofu.abs.init;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.blockentity.AudioControllerBlockEntity;
import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ABSBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(AudioBoundsSystem.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    public static final RegistrySupplier<BlockEntityType<SpeakerBlockEntity>> SPEAKER =
            BLOCK_ENTITIES.register("speaker", () ->
                    BlockEntityType.Builder.of(SpeakerBlockEntity::new, ABSBlocks.SPEAKER.get()).build(null)
            );

    public static final RegistrySupplier<BlockEntityType<AudioControllerBlockEntity>> AUDIO_CONTROLLER =
            BLOCK_ENTITIES.register("audio_controller", () ->
                    BlockEntityType.Builder.of(AudioControllerBlockEntity::new, ABSBlocks.AUDIO_CONTROLLER.get()).build(null)
            );

    public static void register() {
        BLOCK_ENTITIES.register();
    }
}
