package io.github.liquidcatmofu.abs.network;

import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.blockentity.AudioControllerBlockEntity;
import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import io.github.liquidcatmofu.abs.client.ClientPacketHandler;
import io.github.liquidcatmofu.abs.config.AudioControllerTomlConfig;
import io.github.liquidcatmofu.abs.config.SpeakerTomlConfig;
import io.github.liquidcatmofu.abs.data.AudioBounds;
import io.github.liquidcatmofu.abs.data.BoundsShape;
import io.github.liquidcatmofu.abs.data.ControllerRetriggerMode;
import io.github.liquidcatmofu.abs.data.FalloffCurve;
import io.github.liquidcatmofu.abs.data.RedstoneMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ABSNetwork {
    public static final ResourceLocation PLAY_AUDIO = new ResourceLocation("abs", "play_audio");
    public static final ResourceLocation STOP_AUDIO = new ResourceLocation("abs", "stop_audio");
    public static final ResourceLocation SAVE_SPEAKER_CONFIG = new ResourceLocation("abs", "save_speaker_config");
    public static final ResourceLocation SAVE_AUDIO_CONTROLLER_CONFIG =
            new ResourceLocation("abs", "save_audio_controller_config");
    public static final ResourceLocation TEST_AUDIO_CONTROLLER_SIGNAL =
            new ResourceLocation("abs", "test_audio_controller_signal");
    public static final ResourceLocation STOP_AUDIO_CONTROLLER_PLAYBACK =
            new ResourceLocation("abs", "stop_audio_controller_playback");

    private ABSNetwork() {
    }

    public static void registerServerHandlers() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SAVE_SPEAKER_CONFIG, (buf, ctx) -> {
            BlockPos pos = buf.readBlockPos();
            BoundsShape shape = buf.readEnum(BoundsShape.class);
            double radius = buf.readDouble();
            double width = buf.readDouble();
            double depth = buf.readDouble();
            double height = buf.readDouble();
            FalloffCurve curve = buf.readEnum(FalloffCurve.class);
            RedstoneMode redstoneMode = buf.readEnum(RedstoneMode.class);
            String audioFile = buf.readUtf(256);
            boolean subtitleEnabled = buf.readBoolean();
            String trackTitle = buf.readUtf(128);
            String subtitle = buf.readUtf(512);

            ctx.queue(() -> {
                Player player = ctx.getPlayer();
                if (player == null || player.level() == null) {
                    return;
                }

                BlockEntity blockEntity = player.level().getBlockEntity(pos);
                if (!(blockEntity instanceof SpeakerBlockEntity speaker)) {
                    return;
                }

                AudioBounds bounds = new AudioBounds(shape, radius, width, depth, height);
                speaker.applyLoadedConfig(bounds, curve, redstoneMode, audioFile, subtitleEnabled, trackTitle, subtitle);
                SpeakerTomlConfig.save(player.level(), speaker);
                speaker.setChanged();
                speaker.syncConfigToClients();
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SAVE_AUDIO_CONTROLLER_CONFIG, (buf, ctx) -> {
            BlockPos pos = buf.readBlockPos();
            String controllerId = buf.readUtf(128);
            RedstoneMode redstoneMode = buf.readEnum(RedstoneMode.class);
            ControllerRetriggerMode retriggerMode = buf.readEnum(ControllerRetriggerMode.class);

            int targetCount = buf.readVarInt();
            List<BlockPos> targetOffsets = new ArrayList<>(targetCount);
            for (int i = 0; i < targetCount; i++) {
                targetOffsets.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
            }

            int queueCount = buf.readVarInt();
            Map<Integer, List<String>> queues = new HashMap<>();
            for (int i = 0; i < queueCount; i++) {
                int signal = buf.readVarInt();
                int entryCount = buf.readVarInt();
                List<String> queue = new ArrayList<>(entryCount);
                for (int j = 0; j < entryCount; j++) {
                    queue.add(buf.readUtf(256));
                }
                queues.put(signal, queue);
            }

            ctx.queue(() -> {
                Player player = ctx.getPlayer();
                if (player == null || player.level() == null) {
                    return;
                }

                BlockEntity blockEntity = player.level().getBlockEntity(pos);
                if (!(blockEntity instanceof AudioControllerBlockEntity controller)) {
                    return;
                }

                controller.applyLoadedConfig(controllerId, targetOffsets, queues, redstoneMode, retriggerMode);
                AudioControllerTomlConfig.save(player.level(), controller);
                controller.setChanged();
                controller.syncConfigToClients();
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, TEST_AUDIO_CONTROLLER_SIGNAL, (buf, ctx) -> {
            BlockPos pos = buf.readBlockPos();
            int signal = buf.readVarInt();

            ctx.queue(() -> {
                Player player = ctx.getPlayer();
                if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
                    return;
                }

                BlockEntity blockEntity = player.level().getBlockEntity(pos);
                if (!(blockEntity instanceof AudioControllerBlockEntity controller)) {
                    return;
                }

                controller.triggerSignal(serverLevel, signal);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, STOP_AUDIO_CONTROLLER_PLAYBACK, (buf, ctx) -> {
            BlockPos pos = buf.readBlockPos();

            ctx.queue(() -> {
                Player player = ctx.getPlayer();
                if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
                    return;
                }

                BlockEntity blockEntity = player.level().getBlockEntity(pos);
                if (!(blockEntity instanceof AudioControllerBlockEntity controller)) {
                    return;
                }

                controller.stopPlayback(serverLevel);
            });
        });
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientHandlers() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PLAY_AUDIO, (buf, ctx) -> {
            BlockPos pos = buf.readBlockPos();
            UUID token = new UUID(buf.readLong(), buf.readLong());
            String trackTitle = buf.readUtf(128);
            String subtitle = buf.readUtf(512);
            int subtitleDurationTicks = buf.readVarInt();
            ctx.queue(() -> ClientPacketHandler.onPlayAudio(pos, token, trackTitle, subtitle, subtitleDurationTicks));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, STOP_AUDIO, (buf, ctx) -> {
            BlockPos pos = buf.readBlockPos();
            ctx.queue(() -> ClientPacketHandler.onStopAudio(pos));
        });
    }
}
