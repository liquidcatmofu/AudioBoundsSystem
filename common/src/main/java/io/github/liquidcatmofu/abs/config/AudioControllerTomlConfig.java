package io.github.liquidcatmofu.abs.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.blockentity.AudioControllerBlockEntity;
import io.github.liquidcatmofu.abs.data.ControllerRetriggerMode;
import io.github.liquidcatmofu.abs.data.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AudioControllerTomlConfig {
    private AudioControllerTomlConfig() {
    }

    public static void save(Level level, AudioControllerBlockEntity controller) {
        Path path = primaryPath(level, controller.getBlockPos());
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            AudioBoundsSystem.LOGGER.warn("ABS: Failed to create controller TOML config directory {}", path.getParent(), e);
            return;
        }

        try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().preserveInsertionOrder().build()) {
            config.load();
            write(
                    config,
                    controller.getControllerId(),
                    controller.getTargetSpeakerOffsets(),
                    controller.getRedstoneQueues(),
                    controller.getRedstoneMode(),
                    controller.getRetriggerMode(),
                    controller.getBlockPos()
            );
            config.save();
        } catch (Exception e) {
            AudioBoundsSystem.LOGGER.warn("ABS: Failed to save controller TOML config {}", path, e);
        }
    }

    public static void load(Level level, AudioControllerBlockEntity controller) {
        Path path = resolveLoadPath(level, controller.getBlockPos());
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            AudioBoundsSystem.LOGGER.warn("ABS: Failed to create controller TOML config directory {}", path.getParent(), e);
            return;
        }

        try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().preserveInsertionOrder().build()) {
            if (Files.exists(path)) {
                config.load();
            } else {
                writeDefaults(config, controller.getBlockPos());
                config.save();
            }

            controller.applyLoadedConfig(
                    readControllerId(config, controller.getBlockPos()),
                    readTargetSpeakerOffsets(config),
                    readRedstoneQueues(config),
                    readRedstoneMode(config),
                    readRetriggerMode(config)
            );
        } catch (Exception e) {
            AudioBoundsSystem.LOGGER.warn("ABS: Failed to load controller TOML config {}", path, e);
        }
    }

    public static void write(
            CommentedConfig config,
            String controllerId,
            List<BlockPos> targetSpeakerOffsets,
            Map<Integer, List<String>> redstoneQueues,
            RedstoneMode redstoneMode,
            ControllerRetriggerMode retriggerMode,
            BlockPos pos
    ) {
        config.set("controller_id", controllerId == null || controllerId.isBlank() ? fallbackControllerId(pos) : controllerId.trim());
        config.setComment("controller_id", "Controller identifier.");

        List<List<Integer>> targetList = new ArrayList<>();
        for (BlockPos offset : targetSpeakerOffsets) {
            targetList.add(List.of(offset.getX(), offset.getY(), offset.getZ()));
        }
        config.set("target_speakers", targetList);
        config.setComment("target_speakers", "Relative speaker positions as [x, y, z].");

        config.set("redstone.mode", (redstoneMode == null ? RedstoneMode.PULSE : redstoneMode).name());
        config.setComment("redstone.mode", "LEVEL plays while powered and stops on power-off. PULSE triggers only on rising edge.");

        config.set("redstone.retrigger_mode", (retriggerMode == null ? ControllerRetriggerMode.RESTART : retriggerMode).name());
        config.setComment("redstone.retrigger_mode", "When a new trigger arrives during playback: STOP ends playback, RESTART interrupts and starts the new queue.");

        for (int signal = 1; signal <= 15; signal++) {
            List<String> queue = redstoneQueues.get(signal);
            config.set("rs_triggers." + signal + ".queue", queue == null ? List.of() : List.copyOf(queue));
        }
        config.setComment("rs_triggers.15.queue", "Ogg files to play for this signal strength.");
    }

    private static void writeDefaults(CommentedConfig config, BlockPos pos) {
        write(
                config,
                "",
                List.of(new BlockPos(0, 0, 0)),
                Map.of(15, List.of("example.ogg")),
                RedstoneMode.PULSE,
                ControllerRetriggerMode.RESTART,
                pos
        );
    }

    private static String readControllerId(CommentedConfig config, BlockPos pos) {
        Object value = config.get("controller_id");
        String fallback = fallbackControllerId(pos);
        return value instanceof String string && !string.isBlank() ? string.trim() : fallback;
    }

    private static List<BlockPos> readTargetSpeakerOffsets(CommentedConfig config) {
        List<BlockPos> offsets = new ArrayList<>();
        Object rawTargets = config.get("target_speakers");
        if (!(rawTargets instanceof List<?> targets)) {
            return offsets;
        }

        for (Object targetConfig : targets) {
            if (targetConfig instanceof CommentedConfig target) {
                BlockPos offset = readRelativePosition(target);
                if (offset != null) {
                    offsets.add(offset);
                }
                continue;
            }

            if (targetConfig instanceof List<?> values && values.size() >= 3) {
                Integer x = readInt(values.get(0));
                Integer y = readInt(values.get(1));
                Integer z = readInt(values.get(2));
                if (x != null && y != null && z != null) {
                    offsets.add(new BlockPos(x, y, z));
                }
            }
        }

        return offsets;
    }

    private static Map<Integer, List<String>> readRedstoneQueues(CommentedConfig config) {
        Map<Integer, List<String>> queues = new HashMap<>();
        for (int signal = 1; signal <= 15; signal++) {
            Object rawQueue = config.get("rs_triggers." + signal + ".queue");
            if (!(rawQueue instanceof List<?> values)) {
                continue;
            }

            List<String> queue = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof String string && !string.isBlank()) {
                    queue.add(string.trim());
                }
            }
            if (!queue.isEmpty()) {
                queues.put(signal, List.copyOf(queue));
            }
        }
        return queues;
    }

    private static RedstoneMode readRedstoneMode(CommentedConfig config) {
        return RedstoneMode.fromString(config.getOrElse("redstone.mode", RedstoneMode.PULSE.name()));
    }

    private static ControllerRetriggerMode readRetriggerMode(CommentedConfig config) {
        return ControllerRetriggerMode.fromString(
                config.getOrElse("redstone.retrigger_mode", ControllerRetriggerMode.RESTART.name())
        );
    }

    public static Path resolveLoadPath(Level level, BlockPos pos) {
        Path primary = primaryPath(level, pos);
        if (Files.exists(primary)) {
            return primary;
        }

        Path legacy = legacyPath(level, pos);
        return Files.exists(legacy) ? legacy : primary;
    }

    private static Path primaryPath(Level level, BlockPos pos) {
        ResourceLocation id = dimensionLocation(level.dimension());
        return level.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve(AudioBoundsSystem.MOD_ID)
                .resolve("controllers")
                .resolve(dimensionPath(level.dimension()))
                .resolve(fileName(pos, id));
    }

    private static Path legacyPath(Level level, BlockPos pos) {
        ResourceLocation id = dimensionLocation(level.dimension());
        return level.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig")
                .resolve(AudioBoundsSystem.MOD_ID)
                .resolve(id.getNamespace())
                .resolve(id.getPath())
                .resolve("controllers")
                .resolve(fileName(pos, id));
    }

    private static String fileName(BlockPos pos, ResourceLocation dimensionId) {
        return fallbackControllerId(pos) + ".toml";
    }

    private static String fallbackControllerId(BlockPos pos) {
        return "controller_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    private static Path dimensionPath(ResourceKey<Level> dimension) {
        ResourceLocation id = dimensionLocation(dimension);
        if ("minecraft".equals(id.getNamespace())) {
            return Path.of(id.getPath());
        }
        return Path.of(id.getNamespace(), id.getPath());
    }

    private static ResourceLocation dimensionLocation(ResourceKey<Level> dimension) {
        return dimension == null ? Level.OVERWORLD.location() : dimension.location();
    }

    private static BlockPos readRelativePosition(CommentedConfig config) {
        Object rawPosition = config.get("position");
        if (!(rawPosition instanceof List<?> values) || values.size() < 3) {
            return null;
        }

        Integer x = readInt(values.get(0));
        Integer y = readInt(values.get(1));
        Integer z = readInt(values.get(2));
        if (x == null || y == null || z == null) {
            return null;
        }
        return new BlockPos(x, y, z);
    }

    private static Integer readInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
