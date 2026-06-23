package io.github.liquidcatmofu.abs.init;

import dev.architectury.registry.registries.DeferredRegister;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ABSBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(AudioBoundsSystem.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    public static void register() {
        BLOCK_ENTITIES.register();
    }
}
