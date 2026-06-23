package io.github.liquidcatmofu.abs.init;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ABSCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(AudioBoundsSystem.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> ABS_TAB = TABS.register("main",
            () -> CreativeTabRegistry.create(
                    Component.translatable("itemGroup.abs.main"),
                    () -> new ItemStack(ABSBlocks.SPEAKER.get())
            )
    );

    public static void register() {
        TABS.register();
        CreativeTabRegistry.append(ABS_TAB, ABSItems.SPEAKER, ABSItems.AUDIO_CONTROLLER);
    }
}
