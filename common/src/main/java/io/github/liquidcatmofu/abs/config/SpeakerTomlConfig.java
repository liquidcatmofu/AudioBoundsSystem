package io.github.liquidcatmofu.abs.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import dev.architectury.platform.Platform;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import io.github.liquidcatmofu.abs.data.AudioBounds;
import io.github.liquidcatmofu.abs.data.BoundsShape;
import io.github.liquidcatmofu.abs.data.FalloffCurve;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SpeakerTomlConfig {
    public static final int MAX_PACKET_BYTES = 64 * 1024;

    private SpeakerTomlConfig() {
    }

    public static void save(Level level, SpeakerBlockEntity speaker) {
        Path path = pathFor(level, speaker.getBlockPos());
        try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().preserveInsertionOrder().build()) {
            config.load();
            write(config, speaker.getBounds(), speaker.getFalloffCurve(), speaker.getAudioFile());
            config.save();
        } catch (RuntimeException e) {
            AudioBoundsSystem.LOGGER.warn("ABS: Failed to save speaker TOML config {}", path, e);
        }
    }

    public static boolean load(Level level, SpeakerBlockEntity speaker) {
        Path path = pathFor(level, speaker.getBlockPos());
        if (!Files.exists(path)) {
            return false;
        }
        try {
            if (Files.size(path) > MAX_PACKET_BYTES) {
                AudioBoundsSystem.LOGGER.warn("ABS: Speaker TOML config is too large: {}", path);
                return false;
            }
        } catch (IOException e) {
            AudioBoundsSystem.LOGGER.warn("ABS: Failed to stat speaker TOML config {}", path, e);
            return false;
        }

        try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().preserveInsertionOrder().build()) {
            config.load();
            speaker.setBounds(readBounds(config));
            speaker.setFalloffCurve(readFalloffCurve(config));
            speaker.setAudioFile(readAudioFile(config));
            return true;
        } catch (RuntimeException e) {
            AudioBoundsSystem.LOGGER.warn("ABS: Failed to load speaker TOML config {}", path, e);
            return false;
        }
    }

    public static AudioBounds readBounds(CommentedConfig config) {
        BoundsShape shape = BoundsShape.fromString(config.getOrElse("area.shape", BoundsShape.SPHERE.name()));
        double radius = readDouble(config, "area.radius", AudioBounds.DEFAULT.getRadius());
        double width = readDouble(config, "area.width", AudioBounds.DEFAULT.getWidth());
        double depth = readDouble(config, "area.depth", AudioBounds.DEFAULT.getDepth());
        double height = readDouble(config, "area.height", AudioBounds.DEFAULT.getHeight());
        return new AudioBounds(shape, clampPositive(radius), clampPositive(width), clampPositive(depth), clampPositive(height));
    }

    public static FalloffCurve readFalloffCurve(CommentedConfig config) {
        return FalloffCurve.fromString(config.getOrElse("falloff.curve", FalloffCurve.LOGARITHMIC.name()));
    }

    public static String readAudioFile(CommentedConfig config) {
        return config.getOrElse("audio.file", "");
    }

    public static void write(CommentedConfig config, AudioBounds bounds, FalloffCurve curve, String audioFile) {
        config.set("area.shape", bounds.getShape().name());
        config.setComment("area.shape", "Bounds shape: SPHERE, BOX, CYLINDER, or HEMISPHERE.");
        config.set("area.radius", bounds.getRadius());
        config.setComment("area.radius", "Radius for sphere, cylinder, and hemisphere shapes.");
        config.set("area.width", bounds.getWidth());
        config.setComment("area.width", "Box width on the X axis.");
        config.set("area.depth", bounds.getDepth());
        config.setComment("area.depth", "Box depth on the Z axis.");
        config.set("area.height", bounds.getHeight());
        config.setComment("area.height", "Box, cylinder, or hemisphere height.");

        config.set("falloff.curve", curve.name());
        config.setComment("falloff.curve", "Falloff curve: LINEAR, LOGARITHMIC, SMOOTH_STEP, or INVERSE_SQUARE.");

        config.set("audio.file", audioFile == null ? "" : audioFile);
        config.setComment("audio.file", "Audio cache file name resolved under the ABS HTTP cache directory.");
    }

    private static Path pathFor(Level level, BlockPos pos) {
        ResourceKey<Level> dimension = level.dimension();
        ResourceLocation id = dimension.location();
        String dimensionPath = id.getNamespace() + "/" + id.getPath().replace('/', '_');
        return Platform.getConfigFolder()
                .resolve(AudioBoundsSystem.MOD_ID)
                .resolve("speakers")
                .resolve(dimensionPath)
                .resolve(pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + ".toml");
    }

    private static double readDouble(CommentedConfig config, String path, double fallback) {
        Number value = config.get(path);
        return value == null ? fallback : value.doubleValue();
    }

    private static double clampPositive(double value) {
        return Math.max(0.1D, Math.min(1024.0D, value));
    }
}
