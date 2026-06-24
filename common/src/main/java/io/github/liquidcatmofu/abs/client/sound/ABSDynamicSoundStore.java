package io.github.liquidcatmofu.abs.client.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public final class ABSDynamicSoundStore {
    private static final ConcurrentHashMap<ResourceLocation, byte[]> STORE = new ConcurrentHashMap<>();

    private ABSDynamicSoundStore() {}

    public static void put(ResourceLocation loc, byte[] bytes) {
        STORE.put(loc, bytes);
    }

    public static byte[] get(ResourceLocation loc) {
        return STORE.get(loc);
    }

    public static void remove(ResourceLocation loc) {
        STORE.remove(loc);
    }
}
