package io.github.liquidcatmofu.abs.mixin.client;

import com.mojang.blaze3d.audio.OggAudioStream;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.client.sound.ABSDynamicSoundStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Environment(EnvType.CLIENT)
@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {

    @Inject(
        method = "method_19745(Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/sounds/AudioStream;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void abs$injectDynamic(ResourceLocation loc, boolean looping,
                                    CallbackInfoReturnable<AudioStream> cir) {
        if (!"abs".equals(loc.getNamespace())) return;
        if (!loc.getPath().startsWith("sounds/dynamic/")) return;

        byte[] bytes = ABSDynamicSoundStore.get(loc);
        AudioBoundsSystem.LOGGER.warn("ABS [DIAG] SoundBufferMixin: loc={} bytes={}",
                loc, bytes == null ? "null" : bytes.length);
        if (bytes == null) return;

        try {
            AudioStream stream;
            if (looping) {
                stream = new LoopingAudioStream(
                        (is) -> new OggAudioStream(is),
                        new ByteArrayInputStream(bytes)
                );
            } else {
                stream = new OggAudioStream(new ByteArrayInputStream(bytes));
            }
            cir.setReturnValue(stream);
        } catch (IOException e) {
            AudioBoundsSystem.LOGGER.error("ABS: Failed to create audio stream for {}", loc, e);
        }
    }
}
