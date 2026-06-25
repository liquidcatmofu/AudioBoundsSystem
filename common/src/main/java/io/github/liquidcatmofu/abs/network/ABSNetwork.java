package io.github.liquidcatmofu.abs.network;

import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import io.github.liquidcatmofu.abs.client.ClientPacketHandler;
import io.github.liquidcatmofu.abs.config.SpeakerTomlConfig;
import io.github.liquidcatmofu.abs.data.AudioBounds;
import io.github.liquidcatmofu.abs.data.BoundsShape;
import io.github.liquidcatmofu.abs.data.FalloffCurve;
import io.github.liquidcatmofu.abs.data.RedstoneMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

public final class ABSNetwork {
    public static final ResourceLocation PLAY_AUDIO = new ResourceLocation("abs", "play_audio");
    public static final ResourceLocation STOP_AUDIO = new ResourceLocation("abs", "stop_audio");
    public static final ResourceLocation SAVE_SPEAKER_CONFIG = new ResourceLocation("abs", "save_speaker_config");

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
                if (!(ctx.getPlayer() instanceof ServerPlayer player)) {
                    return;
                }
                if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
                    return;
                }
                BlockEntity blockEntity = player.level().getBlockEntity(pos);
                if (!(blockEntity instanceof SpeakerBlockEntity speaker)) {
                    return;
                }

                speaker.setBounds(new AudioBounds(shape, clampPositive(radius), clampPositive(width), clampPositive(depth), clampPositive(height)));
                speaker.setFalloffCurve(curve);
                speaker.setRedstoneMode(redstoneMode);
                speaker.setAudioFile(audioFile);
                speaker.setSubtitleEnabled(subtitleEnabled);
                speaker.setTrackTitle(trackTitle);
                speaker.setSubtitle(subtitle);
                SpeakerTomlConfig.save(player.level(), speaker);
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

    private static double clampPositive(double value) {
        if (!Double.isFinite(value)) {
            return 1.0D;
        }
        return Math.max(0.1D, Math.min(1024.0D, value));
    }
}
