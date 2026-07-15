package io.github.liquidcatmofu.abs.network;

import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.blockentity.AudioControllerBlockEntity;
import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import io.github.liquidcatmofu.abs.client.ClientPacketHandler;
import io.github.liquidcatmofu.abs.client.LibraryEntryInfo;
import io.github.liquidcatmofu.abs.client.LibraryFolderInfo;
import io.github.liquidcatmofu.abs.client.audio.SpeakerAudioManager;
import io.github.liquidcatmofu.abs.client.web.WebRpcClient;
import io.github.liquidcatmofu.abs.client.web.ClientWebServer;
import io.github.liquidcatmofu.abs.config.AudioControllerTomlConfig;
import io.github.liquidcatmofu.abs.config.SpeakerTomlConfig;
import io.github.liquidcatmofu.abs.data.AudioBounds;
import io.github.liquidcatmofu.abs.data.BoundsShape;
import io.github.liquidcatmofu.abs.data.ControllerRetriggerMode;
import io.github.liquidcatmofu.abs.data.FalloffCurve;
import io.github.liquidcatmofu.abs.data.RedstoneMode;
import io.github.liquidcatmofu.abs.library.ABSLibrary;
import io.github.liquidcatmofu.abs.library.AudioEntry;
import io.github.liquidcatmofu.abs.library.FolderAccess;
import io.github.liquidcatmofu.abs.library.LibraryAudio;
import io.github.liquidcatmofu.abs.library.LibraryFolder;
import io.github.liquidcatmofu.abs.library.LibraryRef;
import io.github.liquidcatmofu.abs.library.LibrarySequence;
import io.github.liquidcatmofu.abs.library.LibraryTts;
import io.github.liquidcatmofu.abs.library.SequenceEntry;
import io.github.liquidcatmofu.abs.library.TtsEntry;
import io.github.liquidcatmofu.abs.server.AudioTransferService;
import io.github.liquidcatmofu.abs.server.web.WebRpcService;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ABSNetwork {
    private static final int MAX_CONTROLLER_TARGETS = 256;
    private static final int MAX_CONTROLLER_QUEUES = 15;
    private static final int MAX_QUEUE_ENTRIES = 256;
    private static final double MAX_CONFIG_DISTANCE_SQR = 64.0;
    public static final ResourceLocation PLAY_AUDIO = new ResourceLocation("abs", "play_audio");
    public static final ResourceLocation STOP_AUDIO = new ResourceLocation("abs", "stop_audio");
    public static final ResourceLocation REQUEST_AUDIO_TRANSFER = new ResourceLocation("abs", "request_audio_transfer");
    public static final ResourceLocation AUDIO_TRANSFER_CHUNK = new ResourceLocation("abs", "audio_transfer_chunk");
    public static final ResourceLocation AUDIO_TRANSFER_ERROR = new ResourceLocation("abs", "audio_transfer_error");
    public static final ResourceLocation WEB_RPC_REQUEST_START = new ResourceLocation("abs", "web_rpc_request_start");
    public static final ResourceLocation WEB_RPC_REQUEST_CHUNK = new ResourceLocation("abs", "web_rpc_request_chunk");
    public static final ResourceLocation WEB_RPC_RESPONSE_START = new ResourceLocation("abs", "web_rpc_response_start");
    public static final ResourceLocation WEB_RPC_RESPONSE_CHUNK = new ResourceLocation("abs", "web_rpc_response_chunk");
    public static final ResourceLocation OPEN_WEB_UI = new ResourceLocation("abs", "open_web_ui");
    public static final ResourceLocation SAVE_SPEAKER_CONFIG = new ResourceLocation("abs", "save_speaker_config");
    public static final ResourceLocation SAVE_AUDIO_CONTROLLER_CONFIG =
            new ResourceLocation("abs", "save_audio_controller_config");
    public static final ResourceLocation TEST_AUDIO_CONTROLLER_SIGNAL =
            new ResourceLocation("abs", "test_audio_controller_signal");
    public static final ResourceLocation STOP_AUDIO_CONTROLLER_PLAYBACK =
            new ResourceLocation("abs", "stop_audio_controller_playback");

    // ライブラリブラウザ用パケット
    public static final ResourceLocation REQUEST_LIBRARY_FOLDERS  = new ResourceLocation("abs", "req_lib_folders");
    public static final ResourceLocation LIBRARY_FOLDERS_RESPONSE = new ResourceLocation("abs", "lib_folders_resp");
    public static final ResourceLocation REQUEST_FOLDER_CONTENTS  = new ResourceLocation("abs", "req_folder_contents");
    public static final ResourceLocation FOLDER_CONTENTS_RESPONSE = new ResourceLocation("abs", "folder_contents_resp");

    private ABSNetwork() {}

    public static void registerServerHandlers() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, WEB_RPC_REQUEST_START, (buf, ctx) -> {
            UUID requestId = buf.readUUID();
            String method = buf.readUtf(8);
            String path = buf.readUtf(1024);
            String contentType = buf.readUtf(128);
            String filename = buf.readUtf(1024);
            boolean csrfHeader = buf.readBoolean();
            int totalLength = buf.readVarInt();
            byte[] digest = buf.readByteArray(WebRpcProtocol.DIGEST_BYTES);
            ctx.queue(() -> {
                if (ctx.getPlayer() instanceof ServerPlayer player) {
                    WebRpcService.startRequest(player, requestId, method, path, contentType,
                            filename, csrfHeader, totalLength, digest);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, WEB_RPC_REQUEST_CHUNK, (buf, ctx) -> {
            UUID requestId = buf.readUUID();
            int totalLength = buf.readVarInt();
            int offset = buf.readVarInt();
            byte[] chunk = buf.readByteArray(WebRpcProtocol.MAX_CHUNK_BYTES);
            ctx.queue(() -> {
                if (ctx.getPlayer() instanceof ServerPlayer player) {
                    WebRpcService.acceptChunk(player, requestId, totalLength, offset, chunk);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, REQUEST_AUDIO_TRANSFER, (buf, ctx) -> {
            UUID token = buf.readUUID();
            ctx.queue(() -> {
                if (ctx.getPlayer() instanceof ServerPlayer player) {
                    AudioTransferService.request(player, token);
                }
            });
        });

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
            String displayName = buf.readUtf(128);

            ctx.queue(() -> {
                Player player = ctx.getPlayer();
                if (player == null || player.level() == null) return;

                BlockEntity blockEntity = player.level().getBlockEntity(pos);
                if (!(blockEntity instanceof SpeakerBlockEntity speaker)) return;

                // オーナーまたは OP のみ保存可能
                if (!canModifySpeaker(player, speaker)) return;

                AudioBounds bounds = new AudioBounds(shape, radius, width, depth, height);
                speaker.applyLoadedConfig(bounds, curve, redstoneMode, audioFile, subtitleEnabled,
                        trackTitle, subtitle, displayName);
                speaker.setAudioDisplayName(LibraryRef.resolveDisplayName(audioFile));
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
            if (targetCount < 0 || targetCount > MAX_CONTROLLER_TARGETS) return;
            List<BlockPos> targetOffsets = new ArrayList<>(targetCount);
            for (int i = 0; i < targetCount; i++) {
                targetOffsets.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
            }

            int queueCount = buf.readVarInt();
            if (queueCount < 0 || queueCount > MAX_CONTROLLER_QUEUES) return;
            Map<Integer, List<String>> queues = new HashMap<>();
            for (int i = 0; i < queueCount; i++) {
                int signal = buf.readVarInt();
                int entryCount = buf.readVarInt();
                if (signal < 1 || signal > 15 || entryCount < 0 || entryCount > MAX_QUEUE_ENTRIES) return;
                List<String> queue = new ArrayList<>(entryCount);
                for (int j = 0; j < entryCount; j++) {
                    queue.add(buf.readUtf(256));
                }
                queues.put(signal, queue);
            }

            ctx.queue(() -> {
                Player player = ctx.getPlayer();
                if (player == null || player.level() == null) return;

                BlockEntity blockEntity = player.level().getBlockEntity(pos);
                if (!(blockEntity instanceof AudioControllerBlockEntity controller)) return;
                if (!canModifyController(player, controller, pos)) return;
                if (controller.getOwnerUuid() == null) {
                    controller.setOwnerUuid(player.getUUID());
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
                if (player == null || !(player.level() instanceof ServerLevel serverLevel)) return;

                BlockEntity blockEntity = player.level().getBlockEntity(pos);
                if (!(blockEntity instanceof AudioControllerBlockEntity controller)) return;
                if (!canModifyController(player, controller, pos)) return;

                controller.triggerSignal(serverLevel, signal);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, STOP_AUDIO_CONTROLLER_PLAYBACK, (buf, ctx) -> {
            BlockPos pos = buf.readBlockPos();

            ctx.queue(() -> {
                Player player = ctx.getPlayer();
                if (player == null || !(player.level() instanceof ServerLevel serverLevel)) return;

                BlockEntity blockEntity = player.level().getBlockEntity(pos);
                if (!(blockEntity instanceof AudioControllerBlockEntity controller)) return;
                if (!canModifyController(player, controller, pos)) return;

                controller.stopPlayback(serverLevel);
            });
        });

        // ── ライブラリブラウザ ──────────────────────────────────

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, REQUEST_LIBRARY_FOLDERS, (buf, ctx) -> {
            ctx.queue(() -> {
                Player player = ctx.getPlayer();
                if (!(player instanceof ServerPlayer sp)) return;
                boolean isOp = sp.getServer().getPlayerList().isOp(sp.getGameProfile());
                List<LibraryFolder> folders = ABSLibrary.listAccessible(player.getUUID(), isOp);

                FriendlyByteBuf resp = new FriendlyByteBuf(Unpooled.buffer());
                resp.writeVarInt(folders.size());
                for (LibraryFolder f : folders) {
                    resp.writeUtf(f.id, 128);
                    resp.writeUtf(f.displayName != null ? f.displayName : "", 256);
                    boolean hasParent = f.parentId != null;
                    resp.writeBoolean(hasParent);
                    if (hasParent) resp.writeUtf(f.parentId, 128);
                }
                NetworkManager.sendToPlayer(sp, LIBRARY_FOLDERS_RESPONSE, resp);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, REQUEST_FOLDER_CONTENTS, (buf, ctx) -> {
            String folderId = buf.readUtf(128);

            ctx.queue(() -> {
                Player player = ctx.getPlayer();
                if (!(player instanceof ServerPlayer sp)) return;
                boolean isOp = sp.getServer().getPlayerList().isOp(sp.getGameProfile());
                LibraryFolder folder = ABSLibrary.loadFolder(folderId).orElse(null);
                if (folder == null) return;
                if (ABSLibrary.access(folder, player.getUUID(), isOp) == FolderAccess.NONE) return;

                List<AudioEntry> audioEntries = LibraryAudio.list(folderId);
                List<TtsEntry> ttsEntries = LibraryTts.list(folderId);
                List<SequenceEntry> seqEntries = LibrarySequence.list(folderId);

                FriendlyByteBuf resp = new FriendlyByteBuf(Unpooled.buffer());
                resp.writeUtf(folderId, 128);
                resp.writeVarInt(audioEntries.size());
                for (AudioEntry e : audioEntries) {
                    resp.writeUtf(e.id, 128);
                    resp.writeUtf(e.displayName != null ? e.displayName : "", 256);
                    resp.writeVarInt((int) e.durationTicks);
                }
                resp.writeVarInt(ttsEntries.size());
                for (TtsEntry e : ttsEntries) {
                    resp.writeUtf(e.id, 128);
                    resp.writeUtf(e.displayName != null ? e.displayName : "", 256);
                    resp.writeVarInt((int) e.durationTicks);
                    resp.writeUtf(e.speakerName != null ? e.speakerName : "", 128);
                }
                resp.writeVarInt(seqEntries.size());
                for (SequenceEntry e : seqEntries) {
                    resp.writeUtf(e.id, 128);
                    resp.writeUtf(e.displayName != null ? e.displayName : "", 256);
                }
                NetworkManager.sendToPlayer(sp, FOLDER_CONTENTS_RESPONSE, resp);
            });
        });
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientHandlers() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, OPEN_WEB_UI, (buf, ctx) ->
                ctx.queue(() -> ClientWebServer.INSTANCE.open()));

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PLAY_AUDIO, (buf, ctx) -> {
            PlayAudioPacket packet = PlayAudioPacket.read(buf);
            ctx.queue(() -> ClientPacketHandler.onPlayAudio(
                    packet.pos(), packet.token(), packet.contentHash(), packet.trackTitle(),
                    packet.subtitle(), packet.subtitleDurationTicks()));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, STOP_AUDIO, (buf, ctx) -> {
            BlockPos pos = buf.readBlockPos();
            ctx.queue(() -> ClientPacketHandler.onStopAudio(pos));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AUDIO_TRANSFER_CHUNK, (buf, ctx) -> {
            AudioTransferChunkPacket packet = AudioTransferChunkPacket.read(buf);
            ctx.queue(() -> SpeakerAudioManager.INSTANCE.acceptTransferChunk(
                    packet.token(), packet.totalLength(), packet.offset(), packet.chunk()));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AUDIO_TRANSFER_ERROR, (buf, ctx) -> {
            UUID token = buf.readUUID();
            String message = buf.readUtf(128);
            ctx.queue(() -> SpeakerAudioManager.INSTANCE.failTransfer(token, message));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, WEB_RPC_RESPONSE_START, (buf, ctx) -> {
            UUID requestId = buf.readUUID();
            int status = buf.readVarInt();
            String contentType = buf.readUtf(128);
            int totalLength = buf.readVarInt();
            byte[] digest = buf.readByteArray(WebRpcProtocol.DIGEST_BYTES);
            ctx.queue(() -> WebRpcClient.INSTANCE.startResponse(
                    requestId, status, contentType, totalLength, digest));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, WEB_RPC_RESPONSE_CHUNK, (buf, ctx) -> {
            UUID requestId = buf.readUUID();
            int totalLength = buf.readVarInt();
            int offset = buf.readVarInt();
            byte[] chunk = buf.readByteArray(WebRpcProtocol.MAX_CHUNK_BYTES);
            ctx.queue(() -> WebRpcClient.INSTANCE.acceptResponseChunk(
                    requestId, totalLength, offset, chunk));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, LIBRARY_FOLDERS_RESPONSE, (buf, ctx) -> {
            int count = buf.readVarInt();
            List<LibraryFolderInfo> folders = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String id   = buf.readUtf(128);
                String name = buf.readUtf(256);
                String parentId = buf.readBoolean() ? buf.readUtf(128) : null;
                folders.add(new LibraryFolderInfo(id, name, parentId));
            }
            ctx.queue(() -> ClientPacketHandler.onLibraryFoldersResponse(folders));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, FOLDER_CONTENTS_RESPONSE, (buf, ctx) -> {
            String folderId = buf.readUtf(128);
            int audioCount = buf.readVarInt();
            List<LibraryEntryInfo> audioEntries = new ArrayList<>(audioCount);
            for (int i = 0; i < audioCount; i++) {
                audioEntries.add(new LibraryEntryInfo(
                        buf.readUtf(128), buf.readUtf(256), buf.readVarInt(), "audio", ""));
            }
            int ttsCount = buf.readVarInt();
            List<LibraryEntryInfo> ttsEntries = new ArrayList<>(ttsCount);
            for (int i = 0; i < ttsCount; i++) {
                ttsEntries.add(new LibraryEntryInfo(
                        buf.readUtf(128), buf.readUtf(256), buf.readVarInt(), "tts", buf.readUtf(128)));
            }
            int seqCount = buf.readVarInt();
            List<LibraryEntryInfo> seqEntries = new ArrayList<>(seqCount);
            for (int i = 0; i < seqCount; i++) {
                seqEntries.add(new LibraryEntryInfo(
                        buf.readUtf(128), buf.readUtf(256), 0, "sequence", ""));
            }
            ctx.queue(() -> ClientPacketHandler.onFolderContentsResponse(folderId, audioEntries, ttsEntries, seqEntries));
        });
    }

    /** プレイヤーがスピーカーの設定を変更できるか: オーナーまたは OP。オーナー未設定なら誰でも可。 */
    private static boolean canModifySpeaker(Player player, SpeakerBlockEntity speaker) {
        UUID owner = speaker.getOwnerUuid();
        if (owner == null) return true;
        if (owner.equals(player.getUUID())) return true;
        if (player instanceof ServerPlayer sp) {
            return sp.getServer().getPlayerList().isOp(sp.getGameProfile());
        }
        return false;
    }

    /** コントローラーの所有者またはOPで、対象ブロックの近くにいる場合のみ操作可能。 */
    private static boolean canModifyController(Player player, AudioControllerBlockEntity controller, BlockPos pos) {
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > MAX_CONFIG_DISTANCE_SQR) {
            return false;
        }
        UUID owner = controller.getOwnerUuid();
        if (owner == null || owner.equals(player.getUUID())) return true;
        if (player instanceof ServerPlayer sp) {
            return sp.getServer().getPlayerList().isOp(sp.getGameProfile());
        }
        return false;
    }
}
