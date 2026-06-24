package io.github.liquidcatmofu.abs.blockentity;

import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.config.SpeakerTomlConfig;
import io.github.liquidcatmofu.abs.data.AudioBounds;
import io.github.liquidcatmofu.abs.data.FalloffCurve;
import io.github.liquidcatmofu.abs.init.ABSBlockEntities;
import io.github.liquidcatmofu.abs.network.ABSNetwork;
import io.github.liquidcatmofu.abs.server.ABSHttpServer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class SpeakerBlockEntity extends BlockEntity {
    private static final String KEY_BOUNDS      = "Bounds";
    private static final String KEY_CURVE       = "FalloffCurve";
    private static final String KEY_AUDIO_FILE  = "AudioFile";
    private static final String KEY_TRACK_TITLE = "TrackTitle";
    private static final String KEY_SUBTITLE    = "Subtitle";
    private static final String KEY_SUBTITLE_ENABLED = "SubtitleEnabled";

    private AudioBounds  bounds      = AudioBounds.DEFAULT;
    private FalloffCurve falloffCurve = FalloffCurve.LOGARITHMIC;
    private String       audioFile   = "";
    private String       trackTitle  = "";
    private String       subtitle    = "";
    private boolean      subtitleEnabled = true;

    // 再生状態はトランジェント（NBT 保存しない）
    private boolean playing = false;
    private boolean tomlLoaded = false;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ABSBlockEntities.SPEAKER.get(), pos, state);
    }

    public AudioBounds  getBounds()      { return bounds;       }
    public FalloffCurve getFalloffCurve() { return falloffCurve; }
    public String       getAudioFile()   { return audioFile;    }
    public String       getTrackTitle()  { return trackTitle;   }
    public String       getSubtitle()    { return subtitle;     }
    public boolean      isSubtitleEnabled() { return subtitleEnabled; }
    public boolean      isPlaying()      { return playing;      }

    public void setBounds(AudioBounds bounds) {
        this.bounds = bounds;
        setChanged();
        syncToClients();
    }

    public void setFalloffCurve(FalloffCurve curve) {
        this.falloffCurve = curve;
        setChanged();
        syncToClients();
    }

    public void setAudioFile(String audioFile) {
        this.audioFile = audioFile;
        setChanged();
    }

    public void setTrackTitle(String trackTitle) {
        this.trackTitle = trackTitle == null ? "" : trackTitle;
        setChanged();
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle == null ? "" : subtitle;
        setChanged();
    }

    public void setSubtitleEnabled(boolean subtitleEnabled) {
        this.subtitleEnabled = subtitleEnabled;
        setChanged();
    }

    public void applyLoadedConfig(AudioBounds bounds, FalloffCurve curve, String audioFile, boolean subtitleEnabled, String trackTitle, String subtitle) {
        this.bounds = bounds;
        this.falloffCurve = curve;
        this.audioFile = audioFile == null ? "" : audioFile;
        this.subtitleEnabled = subtitleEnabled;
        this.trackTitle = trackTitle == null ? "" : trackTitle;
        this.subtitle = subtitle == null ? "" : subtitle;
    }

    /** 範囲内の全プレイヤーへ PlayAudioPacket を送信して再生を開始する。 */
    public void startPlaying(ServerLevel level) {
        if (audioFile.isEmpty()) {
            AudioBoundsSystem.LOGGER.warn("ABS: audioFile not configured for SpeakerBlock at {}", worldPosition);
            return;
        }
        if (!ABSHttpServer.isRunning()) {
            AudioBoundsSystem.LOGGER.warn("ABS: HTTP server is not running");
            return;
        }

        Path path = ABSHttpServer.getCacheDir().resolve(audioFile);
        if (!Files.exists(path)) {
            AudioBoundsSystem.LOGGER.warn("ABS: Audio file not found: {}", path);
            return;
        }

        playing = true;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        double maxRange = getMaxRange() * 2;

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= maxRange * maxRange) {
                // プレイヤーごとに個別トークンを発行（トークンは単発使用）
                UUID token = ABSHttpServer.generateToken(path);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeBlockPos(worldPosition);
                buf.writeLong(token.getMostSignificantBits());
                buf.writeLong(token.getLeastSignificantBits());
                buf.writeUtf(subtitleEnabled ? displayTrackTitle() : "", 128);
                buf.writeUtf(subtitleEnabled ? subtitle : "", 512);
                buf.writeVarInt(100);
                NetworkManager.sendToPlayer(player, ABSNetwork.PLAY_AUDIO, buf);
            }
        }
    }

    /** 範囲内の全プレイヤーへ StopAudioPacket を送信して再生を停止する。 */
    public void stopPlaying(ServerLevel level) {
        playing = false;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        double maxRange = getMaxRange() * 2;

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= maxRange * maxRange) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeBlockPos(worldPosition);
                NetworkManager.sendToPlayer(player, ABSNetwork.STOP_AUDIO, buf);
            }
        }
    }

    /** bounds から最大有効半径を算出する。 */
    private double getMaxRange() {
        return switch (bounds.getShape()) {
            case SPHERE, CYLINDER -> bounds.getRadius();
            case BOX              -> Math.max(bounds.getWidth(), Math.max(bounds.getDepth(), bounds.getHeight())) / 2.0;
            case HEMISPHERE       -> Math.max(bounds.getRadius(), bounds.getHeight());
        };
    }

    private String displayTrackTitle() {
        if (!trackTitle.isBlank()) {
            return trackTitle;
        }
        return subtitle.isBlank() ? audioFile : "";
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag boundsTag = new CompoundTag();
        bounds.save(boundsTag);
        tag.put(KEY_BOUNDS, boundsTag);
        tag.putString(KEY_CURVE, falloffCurve.name());
        tag.putString(KEY_AUDIO_FILE, audioFile);
        tag.putString(KEY_TRACK_TITLE, trackTitle);
        tag.putString(KEY_SUBTITLE, subtitle);
        tag.putBoolean(KEY_SUBTITLE_ENABLED, subtitleEnabled);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(KEY_BOUNDS)) {
            bounds = AudioBounds.load(tag.getCompound(KEY_BOUNDS));
        }
        if (tag.contains(KEY_CURVE)) {
            falloffCurve = FalloffCurve.fromString(tag.getString(KEY_CURVE));
        }
        if (tag.contains(KEY_AUDIO_FILE)) {
            audioFile = tag.getString(KEY_AUDIO_FILE);
        }
        if (tag.contains(KEY_TRACK_TITLE)) {
            trackTitle = tag.getString(KEY_TRACK_TITLE);
        }
        if (tag.contains(KEY_SUBTITLE)) {
            subtitle = tag.getString(KEY_SUBTITLE);
        }
        if (tag.contains(KEY_SUBTITLE_ENABLED)) {
            subtitleEnabled = tag.getBoolean(KEY_SUBTITLE_ENABLED);
        }
        loadTomlConfigIfReady();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        loadTomlConfigIfReady();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void loadTomlConfigIfReady() {
        if (tomlLoaded || level == null || level.isClientSide) {
            return;
        }
        tomlLoaded = true;
        SpeakerTomlConfig.load(level, this);
    }
}
