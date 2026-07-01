package io.github.liquidcatmofu.abs.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import io.github.liquidcatmofu.abs.library.LibraryRef;
import io.github.liquidcatmofu.abs.data.AudioBounds;
import io.github.liquidcatmofu.abs.data.BoundsShape;
import io.github.liquidcatmofu.abs.data.FalloffCurve;
import io.github.liquidcatmofu.abs.data.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class SpeakerTomlConfig {
    public static final int MAX_PACKET_BYTES = 64 * 1024;

    private SpeakerTomlConfig() {
    }

    public static void save(Level level, SpeakerBlockEntity speaker) {
        Path path = pathFor(level, speaker.getBlockPos());
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            AudioBoundsSystem.LOGGER.warn("ABS: Failed to create speaker TOML config directory {}", path.getParent(), e);
            return;
        }
        try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().preserveInsertionOrder().build()) {
            config.load();
            write(config, speaker.getBounds(), speaker.getFalloffCurve(), speaker.getRedstoneMode(),
                    speaker.getAudioFile(), speaker.isSubtitleEnabled(), speaker.getTrackTitle(),
                    speaker.getSubtitle(), speaker.getDisplayName(), speaker.getOwnerUuid());
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
            speaker.applyLoadedConfig(
                    readBounds(config),
                    readFalloffCurve(config),
                    readRedstoneMode(config),
                    readAudioFile(config),
                    readSubtitleEnabled(config),
                    readTrackTitle(config),
                    readSubtitle(config),
                    readDisplayName(config)
            );
            UUID ownerUuid = readOwnerUuid(config);
            if (ownerUuid != null) {
                speaker.setOwnerUuid(ownerUuid);
            }
            speaker.setAudioDisplayName(LibraryRef.resolveDisplayName(speaker.getAudioFile()));
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

    public static RedstoneMode readRedstoneMode(CommentedConfig config) {
        return RedstoneMode.fromString(config.getOrElse("redstone.mode", RedstoneMode.LEVEL.name()));
    }

    public static String readDisplayName(CommentedConfig config) {
        return config.getOrElse("display.name", "");
    }

    public static UUID readOwnerUuid(CommentedConfig config) {
        String uuidStr = config.getOrElse("owner.uuid", "");
        if (uuidStr.isBlank()) return null;
        try { return UUID.fromString(uuidStr); } catch (IllegalArgumentException e) { return null; }
    }

    public static String readAudioFile(CommentedConfig config) {
        return config.getOrElse("audio.file", "");
    }

    public static boolean readSubtitleEnabled(CommentedConfig config) {
        return config.getOrElse("display.subtitle_enabled", true);
    }

    public static String readTrackTitle(CommentedConfig config) {
        return config.getOrElse("display.track_title", "");
    }

    public static String readSubtitle(CommentedConfig config) {
        return config.getOrElse("display.subtitle", "");
    }

    public static void write(CommentedConfig config, AudioBounds bounds, FalloffCurve curve, RedstoneMode redstoneMode, String audioFile, boolean subtitleEnabled, String trackTitle, String subtitle, String displayName, UUID ownerUuid) {
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
        config.set("redstone.mode", redstoneMode.name());
        config.setComment("redstone.mode", "Redstone mode: LEVEL follows signal on/off, PULSE toggles playback on rising edge.");

        config.set("audio.file", audioFile == null ? "" : audioFile);
        config.setComment("audio.file", "Audio cache file name resolved under the ABS HTTP cache directory.");

        config.set("display.subtitle_enabled", subtitleEnabled);
        config.setComment("display.subtitle_enabled", "Set false to suppress the track title and subtitle HUD for this speaker.");
        config.set("display.track_title", trackTitle == null ? "" : trackTitle);
        config.setComment("display.track_title", "Optional title shown above subtitles. Empty values fall back to audio.file.");
        config.set("display.subtitle", subtitle == null ? "" : subtitle);
        config.setComment("display.subtitle", "Optional subtitle text shown while the audio starts playing.");

        config.set("display.name", displayName == null ? "" : displayName);
        config.setComment("display.name", "Human-readable name for this speaker, shown in the ABS dashboard.");
        if (ownerUuid != null) {
            config.set("owner.uuid", ownerUuid.toString());
            config.setComment("owner.uuid", "UUID of the player who placed this speaker (used for access control).");
        }
    }

    private static Path pathFor(Level level, BlockPos pos) {
        ResourceKey<Level> dimension = level.dimension();
        ResourceLocation id = dimension.location();
        String dimensionPath = id.getNamespace() + "/" + id.getPath().replace('/', '_');
        if (level.getServer() == null) {
            throw new IllegalStateException("Speaker TOML config path requires a server level");
        }
        return level.getServer().getWorldPath(LevelResource.ROOT)
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
