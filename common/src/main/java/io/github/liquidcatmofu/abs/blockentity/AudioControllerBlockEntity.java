package io.github.liquidcatmofu.abs.blockentity;

import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.audio.OggAudioDuration;
import io.github.liquidcatmofu.abs.config.AudioControllerTomlConfig;
import io.github.liquidcatmofu.abs.data.ControllerRetriggerMode;
import io.github.liquidcatmofu.abs.data.RedstoneMode;
import io.github.liquidcatmofu.abs.init.ABSBlockEntities;
import io.github.liquidcatmofu.abs.library.LibraryRef;
import io.github.liquidcatmofu.abs.library.LibrarySequence;
import io.github.liquidcatmofu.abs.library.SequenceStep;
import io.github.liquidcatmofu.abs.server.AudioTransferService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AudioControllerBlockEntity extends BlockEntity {
    private static final String KEY_CONTROLLER_ID       = "ControllerId";
    private static final String KEY_TARGET_SPEAKERS      = "TargetSpeakers";
    private static final String KEY_REDSTONE_QUEUES      = "RedstoneQueues";
    private static final String KEY_QUEUE_DISPLAY_NAMES  = "QueueDisplayNames";
    private static final String KEY_REDSTONE_MODE        = "RedstoneMode";
    private static final String KEY_RETRIGGER_MODE       = "RetriggerMode";
    private static final String KEY_OWNER_UUID           = "OwnerUuid";

    private String controllerId = "";
    private List<BlockPos> targetSpeakerOffsets = List.of();
    private Map<Integer, List<String>> redstoneQueues     = Map.of();
    private Map<Integer, List<String>> queueDisplayNames  = Map.of();
    private RedstoneMode redstoneMode = RedstoneMode.PULSE;
    private ControllerRetriggerMode retriggerMode = ControllerRetriggerMode.RESTART;
    private UUID ownerUuid;
    private int lastRedstoneSignal;
    private boolean needsInitialRedstoneSync = true;
    private boolean tomlLoaded;

    private List<String> activeQueue = List.of();
    private int activeQueueIndex;
    private boolean activeQueueHadPlayableTrack;
    private int pendingRestartSignal;
    private long nextTrackTick = -1L;

    public AudioControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ABSBlockEntities.AUDIO_CONTROLLER.get(), pos, state);
    }

    public String getControllerId() {
        return controllerId;
    }

    public List<BlockPos> getTargetSpeakerOffsets() {
        return targetSpeakerOffsets;
    }

    public Map<Integer, List<String>> getRedstoneQueues() {
        return redstoneQueues;
    }

    public Map<Integer, List<String>> getQueueDisplayNames() {
        return queueDisplayNames;
    }

    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    public ControllerRetriggerMode getRetriggerMode() {
        return retriggerMode;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        setChanged();
    }

    public void applyLoadedConfig(
            String controllerId,
            List<BlockPos> targetSpeakerOffsets,
            Map<Integer, List<String>> redstoneQueues,
            RedstoneMode redstoneMode,
            ControllerRetriggerMode retriggerMode
    ) {
        this.controllerId         = controllerId == null ? "" : controllerId;
        this.targetSpeakerOffsets = List.copyOf(targetSpeakerOffsets);
        this.redstoneQueues       = Map.copyOf(redstoneQueues);
        this.redstoneMode         = redstoneMode == null ? RedstoneMode.PULSE : redstoneMode;
        this.retriggerMode        = retriggerMode == null ? ControllerRetriggerMode.RESTART : retriggerMode;
        setChanged();
    }

    public void syncConfigToClients() {
        syncToClients();
    }

    public void syncRedstoneState(int signal) {
        int clampedSignal = Math.max(0, Math.min(15, signal));
        int previousSignal = lastRedstoneSignal;
        lastRedstoneSignal = clampedSignal;

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        switch (redstoneMode) {
            case LEVEL -> {
                if (clampedSignal <= 0) {
                    if (previousSignal > 0) {
                        stopPlayback(serverLevel);
                    }
                    return;
                }

                if (previousSignal != clampedSignal) {
                    triggerSignal(serverLevel, clampedSignal);
                }
            }
            case PULSE -> {
                boolean risingEdge = previousSignal == 0 && clampedSignal > 0;
                if (risingEdge) {
                    triggerSignal(serverLevel, clampedSignal);
                }
            }
        }
    }

    public void triggerSignal(ServerLevel level, int signal) {
        int clampedSignal = Math.max(1, Math.min(15, signal));
        if (isPlaybackActive()) {
            switch (retriggerMode) {
                case STOP -> {
                    stopPlayback(level);
                    return;
                }
                case RESTART -> stopPlayback(level);
            }
        }

        startQueueForSignal(level, clampedSignal);
    }

    public void stopPlayback(ServerLevel level) {
        stopQueue();
        stopTargets(level);
    }

    private boolean isPlaybackActive() {
        return nextTrackTick >= 0L || !activeQueue.isEmpty();
    }

    private void startQueueForSignal(ServerLevel level, int signal) {
        List<String> queue = redstoneQueues.get(signal);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        activeQueue = expandRefs(queue);
        activeQueueIndex = 0;
        activeQueueHadPlayableTrack = false;
        pendingRestartSignal = 0;
        nextTrackTick = level.getGameTime();
        runNextQueueEntry(level);
    }

    /** シーケンス ref をステップの audioRef に展開し、通常 ref はそのまま渡す。 */
    private List<String> expandRefs(List<String> refs) {
        List<String> out = new ArrayList<>();
        for (String ref : refs) {
            if (ref.startsWith(LibraryRef.PREFIX)) {
                String body  = ref.substring(LibraryRef.PREFIX.length());
                String[] parts = body.split("/", 3);
                if (parts.length == 3 && "sequence".equals(parts[1])) {
                    LibrarySequence.load(parts[0], parts[2]).ifPresent(seq -> {
                        for (SequenceStep step : seq.steps) {
                            if (step.audioRef != null && !step.audioRef.isBlank()) {
                                out.add(step.audioRef);
                            }
                        }
                    });
                    continue;
                }
            }
            out.add(ref);
        }
        return List.copyOf(out);
    }

    private void runNextQueueEntry(ServerLevel level) {
        while (activeQueueIndex < activeQueue.size()) {
            String audioFile = activeQueue.get(activeQueueIndex++).trim();
            if (audioFile.isEmpty()) {
                continue;
            }

            int durationTicks = playTrackOnTargets(level, audioFile);
            if (durationTicks > 0) {
                activeQueueHadPlayableTrack = true;
                nextTrackTick = level.getGameTime() + durationTicks;
                return;
            }
        }

        if (redstoneMode == RedstoneMode.LEVEL && lastRedstoneSignal > 0 && activeQueueHadPlayableTrack) {
            scheduleQueueRestart(level, lastRedstoneSignal);
            return;
        }

        stopQueue();
    }

    private int playTrackOnTargets(ServerLevel level, String audioFile) {
        if (!AudioTransferService.isRunning()) {
            return -1;
        }

        Path path = LibraryRef.resolve(audioFile).orElse(null);
        if (path == null) {
            AudioBoundsSystem.LOGGER.warn("ABS: Controller at {} could not resolve audio ref: {}", worldPosition, audioFile);
            return -1;
        }

        int durationTicks;
        try {
            durationTicks = (int) Math.max(1L, OggAudioDuration.readDurationTicks(path));
        } catch (IOException e) {
            AudioBoundsSystem.LOGGER.warn("ABS: Controller at {} failed to read duration for {}", worldPosition, path, e);
            return -1;
        }

        int startedSpeakers = 0;
        List<BlockPos> missingTargets = new ArrayList<>();
        for (BlockPos relativePos : targetSpeakerOffsets) {
            BlockPos targetPos = worldPosition.offset(relativePos);
            if (!(level.getBlockEntity(targetPos) instanceof SpeakerBlockEntity speaker)) {
                missingTargets.add(targetPos);
                continue;
            }

            String previousAudioFile = speaker.getAudioFile();
            speaker.setAudioFile(audioFile);
            speaker.startPlaying(level);
            speaker.setAudioFile(previousAudioFile);
            startedSpeakers++;
        }

        if (startedSpeakers == 0) {
            AudioBoundsSystem.LOGGER.warn(
                    "ABS: Controller at {} has no valid target speakers for {}. Checked positions: {}",
                    worldPosition,
                    audioFile,
                    missingTargets
            );
        }

        return durationTicks;
    }

    private void stopQueue() {
        activeQueue = List.of();
        activeQueueIndex = 0;
        activeQueueHadPlayableTrack = false;
        pendingRestartSignal = 0;
        nextTrackTick = -1L;
    }

    private void scheduleQueueRestart(ServerLevel level, int signal) {
        activeQueue = List.of();
        activeQueueIndex = 0;
        activeQueueHadPlayableTrack = false;
        pendingRestartSignal = signal;
        nextTrackTick = level.getGameTime() + 1L;
    }

    private void stopTargets(ServerLevel level) {
        for (BlockPos relativePos : targetSpeakerOffsets) {
            BlockPos targetPos = worldPosition.offset(relativePos);
            if (level.getBlockEntity(targetPos) instanceof SpeakerBlockEntity speaker) {
                speaker.stopPlaying(level);
            }
        }
    }

    private void syncToClients() {
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(KEY_CONTROLLER_ID, controllerId);
        tag.put(KEY_TARGET_SPEAKERS, writeTargetSpeakers());
        tag.put(KEY_REDSTONE_QUEUES, writeRedstoneQueues());
        tag.putString(KEY_REDSTONE_MODE, redstoneMode.name());
        tag.putString(KEY_RETRIGGER_MODE, retriggerMode.name());
        if (ownerUuid != null) {
            tag.putUUID(KEY_OWNER_UUID, ownerUuid);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        controllerId        = tag.getString(KEY_CONTROLLER_ID);
        targetSpeakerOffsets = readTargetSpeakers(tag.getList(KEY_TARGET_SPEAKERS, Tag.TAG_COMPOUND));
        redstoneQueues      = readRedstoneQueues(tag.getCompound(KEY_REDSTONE_QUEUES));
        queueDisplayNames   = readQueueDisplayNames(tag.getCompound(KEY_QUEUE_DISPLAY_NAMES));
        redstoneMode        = RedstoneMode.fromString(tag.getString(KEY_REDSTONE_MODE));
        retriggerMode       = ControllerRetriggerMode.fromString(tag.getString(KEY_RETRIGGER_MODE));
        ownerUuid           = tag.hasUUID(KEY_OWNER_UUID) ? tag.getUUID(KEY_OWNER_UUID) : null;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        needsInitialRedstoneSync = false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AudioControllerBlockEntity controller) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (controller.needsInitialRedstoneSync) {
            controller.needsInitialRedstoneSync = false;
            controller.syncRedstoneState(serverLevel.getBestNeighborSignal(pos));
        }

        if (controller.nextTrackTick >= 0L && serverLevel.getGameTime() >= controller.nextTrackTick) {
            if (controller.pendingRestartSignal > 0) {
                int signal = controller.pendingRestartSignal;
                controller.pendingRestartSignal = 0;
                controller.startQueueForSignal(serverLevel, signal);
                return;
            }

            controller.runNextQueueEntry(serverLevel);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        // 表示名はセーブ不要。サーバー側で毎回動的解決してクライアントに送信する
        CompoundTag names = new CompoundTag();
        for (Map.Entry<Integer, List<String>> e : redstoneQueues.entrySet()) {
            ListTag list = new ListTag();
            for (String ref : e.getValue()) list.add(StringTag.valueOf(LibraryRef.resolveDisplayName(ref)));
            names.put(Integer.toString(e.getKey()), list);
        }
        tag.put(KEY_QUEUE_DISPLAY_NAMES, names);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private ListTag writeTargetSpeakers() {
        ListTag list = new ListTag();
        for (BlockPos offset : targetSpeakerOffsets) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", offset.getX());
            entry.putInt("Y", offset.getY());
            entry.putInt("Z", offset.getZ());
            list.add(entry);
        }
        return list;
    }

    private CompoundTag writeRedstoneQueues() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<Integer, List<String>> entry : redstoneQueues.entrySet()) {
            ListTag queueList = new ListTag();
            for (String audioFile : entry.getValue()) {
                queueList.add(StringTag.valueOf(audioFile));
            }
            tag.put(Integer.toString(entry.getKey()), queueList);
        }
        return tag;
    }

    private List<BlockPos> readTargetSpeakers(ListTag list) {
        List<BlockPos> offsets = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            offsets.add(new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")));
        }
        return List.copyOf(offsets);
    }

    private Map<Integer, List<String>> readQueueDisplayNames(CompoundTag tag) {
        Map<Integer, List<String>> result = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            try {
                int signal = Integer.parseInt(key);
                ListTag list = tag.getList(key, Tag.TAG_STRING);
                List<String> names = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) names.add(list.getString(i));
                result.put(signal, List.copyOf(names));
            } catch (NumberFormatException ignored) {}
        }
        return Map.copyOf(result);
    }

    private Map<Integer, List<String>> readRedstoneQueues(CompoundTag tag) {
        Map<Integer, List<String>> queues = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            try {
                int signal = Integer.parseInt(key);
                ListTag queueList = tag.getList(key, Tag.TAG_STRING);
                List<String> queue = new ArrayList<>();
                for (int i = 0; i < queueList.size(); i++) {
                    queue.add(queueList.getString(i));
                }
                if (!queue.isEmpty()) {
                    queues.put(signal, List.copyOf(queue));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return Map.copyOf(queues);
    }

    private void loadTomlConfigIfReady() {
        if (tomlLoaded || !(level instanceof ServerLevel)) {
            return;
        }

        if (!controllerId.isBlank() || !targetSpeakerOffsets.isEmpty() || !redstoneQueues.isEmpty()) {
            tomlLoaded = true;
            return;
        }

        tomlLoaded = true;
    }
}
