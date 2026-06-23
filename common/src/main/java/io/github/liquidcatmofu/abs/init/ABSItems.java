package io.github.liquidcatmofu.abs.init;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class ABSItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(AudioBoundsSystem.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> SPEAKER =
            ITEMS.register("speaker", () -> new BlockItem(ABSBlocks.SPEAKER.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> AUDIO_CONTROLLER =
            ITEMS.register("audio_controller", () -> new BlockItem(ABSBlocks.AUDIO_CONTROLLER.get(), new Item.Properties()));

    public static void register() {
        ITEMS.register();
    }
}
