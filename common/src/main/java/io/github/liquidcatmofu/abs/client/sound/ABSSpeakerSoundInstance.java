package io.github.liquidcatmofu.abs.client.sound;

import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class ABSSpeakerSoundInstance extends AbstractSoundInstance implements TickableSoundInstance {
    private final ResourceLocation dynamicResourceLoc;
    private final BlockPos speakerPos;

    public ABSSpeakerSoundInstance(ResourceLocation dynamicResourceLoc, BlockPos pos) {
        super(toSoundId(dynamicResourceLoc), SoundSource.BLOCKS, RandomSource.create());
        this.dynamicResourceLoc = dynamicResourceLoc;
        this.speakerPos = pos;
        this.looping = false;
        this.attenuation = Attenuation.NONE;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager soundManager) {
        ResourceLocation soundId = toSoundId(dynamicResourceLoc);
        AudioBoundsSystem.LOGGER.debug("ABS [DIAG] resolve: soundId={} path={}", soundId, "abs:sounds/" + soundId.getPath() + ".ogg");
        this.sound = new Sound(
                soundId.toString(),
                ConstantFloat.of(1.0F),
                ConstantFloat.of(1.0F),
                1,
                Sound.Type.FILE,
                true,
                false,
                16
        );
        WeighedSoundEvents events = new WeighedSoundEvents(soundId, null);
        events.addSound(this.sound);
        return events;
    }

    @Override
    public float getVolume() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return 0.0F;
        }
        if (!(mc.level.getBlockEntity(speakerPos) instanceof SpeakerBlockEntity be)) {
            return 0.0F;
        }

        Vec3 p = mc.player.position();
        double dx = p.x - (speakerPos.getX() + 0.5);
        double dy = p.y - (speakerPos.getY() + 0.5);
        double dz = p.z - (speakerPos.getZ() + 0.5);
        double t = be.getBounds().normalizedDistance(dx, dy, dz);
        return (float) be.getFalloffCurve().gain(t);
    }

    @Override
    public void tick() {}

    @Override
    public boolean isStopped() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || !(mc.level.getBlockEntity(speakerPos) instanceof SpeakerBlockEntity);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(speakerPos) instanceof SpeakerBlockEntity;
    }

    public ResourceLocation getDynamicResourceLoc() {
        return dynamicResourceLoc;
    }

    public BlockPos getSpeakerPos() {
        return speakerPos;
    }

    private static ResourceLocation toSoundId(ResourceLocation dynamicResourceLoc) {
        String path = dynamicResourceLoc.getPath();
        if (path.startsWith("sounds/")) {
            path = path.substring("sounds/".length());
        }
        if (path.endsWith(".ogg")) {
            path = path.substring(0, path.length() - ".ogg".length());
        }
        return new ResourceLocation(dynamicResourceLoc.getNamespace(), path);
    }
}
