package io.github.liquidcatmofu.abs.client.gui;

import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.blockentity.AudioControllerBlockEntity;
import io.github.liquidcatmofu.abs.data.ControllerRetriggerMode;
import io.github.liquidcatmofu.abs.data.RedstoneMode;
import io.github.liquidcatmofu.abs.network.ABSNetwork;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public final class AudioControllerConfigScreen extends Screen {
    private static final int PANEL_WIDTH  = 340;
    private static final int MAX_QUEUE    = 8;
    private static final int VISIBLE_ROWS = 3;
    private static final int ROW_H        = 20;

    // top=16 基準の相対 Y。
    // 合計: 16 + 194 + 20(ボタン高) = 230px → 240px 画面に収まる
    private static final int Y_NAME      = 0;
    private static final int Y_POS       = 22;
    private static final int Y_REDSTONE  = 44;
    private static final int Y_RETRIGGER = 66;
    private static final int Y_SIG_BTN   = 88;   // [<] シグナル [>]、ラベルは同行中央に描画
    private static final int Y_QUEUE     = 110;
    private static final int Y_ADD       = Y_QUEUE + VISIBLE_ROWS * ROW_H + 2; // 172
    private static final int Y_ACTION    = Y_ADD + 22;                          // 194

    private record QueueItem(String ref, String label) {
        static QueueItem empty() { return new QueueItem("", ""); }
        boolean hasRef() { return !ref.isEmpty(); }
    }

    private final BlockPos pos;
    private final Screen   parent;

    private EditBox controllerId;
    private EditBox targetPositions;
    private Component error;

    // rebuildWidgets 後もテキスト入力を保持するための draft フィールド
    private String controllerIdDraft    = null;
    private String targetPositionsDraft = null;

    private int selectedSignal  = 15;
    private RedstoneMode            redstoneMode  = RedstoneMode.PULSE;
    private ControllerRetriggerMode retriggerMode = ControllerRetriggerMode.RESTART;

    private final Map<Integer, List<QueueItem>> queueDrafts = new HashMap<>();
    private int queueScroll = 0;

    public AudioControllerConfigScreen(BlockPos pos, Screen parent) {
        super(Component.literal("ABS Audio Controller"));
        this.pos    = pos;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top  = 16;

        AudioControllerBlockEntity controller = getController();
        if (controller != null && queueDrafts.isEmpty()) {
            redstoneMode  = controller.getRedstoneMode();
            retriggerMode = controller.getRetriggerMode();
            Map<Integer, List<String>> displayNames = controller.getQueueDisplayNames();
            for (Map.Entry<Integer, List<String>> e : controller.getRedstoneQueues().entrySet()) {
                List<String> names = displayNames.getOrDefault(e.getKey(), List.of());
                List<QueueItem> items = new ArrayList<>();
                for (int i = 0; i < e.getValue().size(); i++) {
                    String ref  = e.getValue().get(i);
                    String name = i < names.size() ? names.get(i) : "";
                    items.add(new QueueItem(ref, name));
                }
                queueDrafts.put(e.getKey(), items);
            }
        }

        // コントローラー名（= ID）
        if (controllerIdDraft == null)
            controllerIdDraft = controller == null ? "" : controller.getControllerId();
        controllerId = new EditBox(font, left, top + Y_NAME, PANEL_WIDTH, 20,
                Component.literal("Controller Name"));
        controllerId.setMaxLength(128);
        controllerId.setHint(Component.literal("コントローラー名 / ID"));
        controllerId.setValue(controllerIdDraft);
        addRenderableWidget(controllerId);

        // スピーカー座標リスト
        if (targetPositionsDraft == null)
            targetPositionsDraft = controller == null ? "" : formatAbsoluteTargets(controller.getTargetSpeakerOffsets());
        targetPositions = new EditBox(font, left, top + Y_POS, PANEL_WIDTH, 20,
                Component.literal("Speaker positions"));
        targetPositions.setMaxLength(1024);
        targetPositions.setHint(Component.literal("x,y,z; x,y,z ..."));
        targetPositions.setValue(targetPositionsDraft);
        addRenderableWidget(targetPositions);

        // Redstone / Retrigger モード
        addRenderableWidget(CycleButton.<RedstoneMode>builder(v -> Component.literal(v.name()))
                .withValues(RedstoneMode.values()).withInitialValue(redstoneMode)
                .create(left, top + Y_REDSTONE, PANEL_WIDTH, 20, Component.literal("Redstone Mode"),
                        (btn, v) -> redstoneMode = v));
        addRenderableWidget(CycleButton.<ControllerRetriggerMode>builder(v -> Component.literal(v.name()))
                .withValues(ControllerRetriggerMode.values()).withInitialValue(retriggerMode)
                .create(left, top + Y_RETRIGGER, PANEL_WIDTH, 20, Component.literal("Retrigger Mode"),
                        (btn, v) -> retriggerMode = v));

        // シグナル選択（[<] ... [>]、ラベルは render() で中央に描画）
        addRenderableWidget(Button.builder(Component.literal("<"), btn -> changeSignal(-1))
                .bounds(left, top + Y_SIG_BTN, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), btn -> changeSignal(1))
                .bounds(left + PANEL_WIDTH - 24, top + Y_SIG_BTN, 24, 20).build());

        // キュー行
        List<QueueItem> items = currentItems();
        int queueLeft = left + 28;
        int queueW    = PANEL_WIDTH - 56;
        int browseW   = 54;
        int removeW   = 18;
        int nameW     = queueW - browseW - removeW - 4;
        int queueTop  = top + Y_QUEUE;

        int visibleFrom = Math.min(queueScroll, Math.max(0, items.size() - VISIBLE_ROWS));
        queueScroll = visibleFrom;
        int visibleTo = Math.min(visibleFrom + VISIBLE_ROWS, items.size());

        for (int i = visibleFrom; i < visibleTo; i++) {
            final int idx = i;
            int rowY = queueTop + (i - visibleFrom) * ROW_H;
            addRenderableWidget(Button.builder(Component.literal("Browse"), btn -> openBrowserFor(idx))
                    .bounds(queueLeft + nameW + 2, rowY, browseW, 20).build());
            addRenderableWidget(Button.builder(Component.literal("✕"), btn -> removeItem(idx))
                    .bounds(queueLeft + nameW + 2 + browseW + 2, rowY, removeW, 20).build());
        }

        if (items.size() > VISIBLE_ROWS) {
            addRenderableWidget(Button.builder(Component.literal("▲"),
                    btn -> { if (queueScroll > 0) { queueScroll--; rebuildWidgets(); } })
                .bounds(left + PANEL_WIDTH + 2, queueTop, 14, 14).build());
            addRenderableWidget(Button.builder(Component.literal("▼"),
                    btn -> { if (queueScroll + VISIBLE_ROWS < items.size()) { queueScroll++; rebuildWidgets(); } })
                .bounds(left + PANEL_WIDTH + 2, queueTop + (VISIBLE_ROWS - 1) * ROW_H, 14, 14).build());
        }

        // 追加ボタン
        int addY = top + Y_ADD;
        if (items.size() < MAX_QUEUE) {
            addRenderableWidget(Button.builder(Component.literal("＋ 追加"), btn -> addItem())
                    .bounds(queueLeft, addY, 60, 20).build());
        }

        // アクションボタン
        int actionY = top + Y_ACTION;
        addRenderableWidget(Button.builder(Component.literal("Play Test"), btn -> testSelectedSignal())
                .bounds(left, actionY, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Stop"), btn -> stopPlayback())
                .bounds(left + 86, actionY, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
                .bounds(left + 152, actionY, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .bounds(left + 238, actionY, 102, 20).build());

        setInitialFocus(controllerId);
    }

    @Override
    protected void rebuildWidgets() {
        // 入力中のテキストを draft に退避してから再構築
        if (controllerId    != null) controllerIdDraft    = controllerId.getValue();
        if (targetPositions != null) targetPositionsDraft = targetPositions.getValue();
        clearWidgets();
        init();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top  = 16;

        g.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);

        // シグナルラベル（シグナル選択ボタンと同行に中央描画）
        g.drawCenteredString(font, Component.literal("Signal " + selectedSignal + " Queue"),
                left + PANEL_WIDTH / 2, top + Y_SIG_BTN + 6, 0xFFFFFF);

        // キューアイテムの表示名
        List<QueueItem> items = currentItems();
        int queueLeft = left + 28;
        int queueW    = PANEL_WIDTH - 56;
        int browseW   = 54;
        int removeW   = 18;
        int nameW     = queueW - browseW - removeW - 4;
        int queueTop  = top + Y_QUEUE;
        int visibleFrom = queueScroll;
        int visibleTo   = Math.min(visibleFrom + VISIBLE_ROWS, items.size());

        for (int i = visibleFrom; i < visibleTo; i++) {
            QueueItem item = items.get(i);
            int rowY = queueTop + (i - visibleFrom) * ROW_H + 6;
            String label = item.hasRef()
                    ? (item.label().isEmpty() ? "（名称不明）" : item.label())
                    : "（未選択）";
            int color = item.hasRef() ? 0xFFFFFF : 0x808080;
            g.drawString(font, fitText(label, nameW), queueLeft, rowY, color, false);
        }

        if (items.isEmpty()) {
            g.drawString(font, "「＋ 追加」でエントリを追加",
                    queueLeft, queueTop + 6, 0x606060, false);
        }

        if (error != null) {
            g.drawCenteredString(font, error, width / 2, top + Y_ACTION + 24, 0xFF8080);
        }
    }

    // ── キュー操作 ──────────────────────────────────────────────

    private void openBrowserFor(int idx) {
        Minecraft.getInstance().setScreen(new LibraryBrowserScreen(this, true, (ref, label) -> {
            List<QueueItem> items = currentItems();
            if (idx < items.size()) {
                items.set(idx, new QueueItem(ref, label));
            }
        }));
    }

    private void addItem() {
        List<QueueItem> items = currentItems();
        if (items.size() < MAX_QUEUE) {
            items.add(QueueItem.empty());
            queueScroll = Math.max(0, items.size() - VISIBLE_ROWS);
            rebuildWidgets();
        }
    }

    private void removeItem(int idx) {
        List<QueueItem> items = queueDrafts.get(selectedSignal);
        if (items != null && idx < items.size()) {
            items.remove(idx);
            queueScroll = Math.max(0, Math.min(queueScroll, items.size() - VISIBLE_ROWS));
            rebuildWidgets();
        }
    }

    private List<QueueItem> currentItems() {
        return queueDrafts.computeIfAbsent(selectedSignal, k -> new ArrayList<>());
    }

    private void changeSignal(int delta) {
        selectedSignal += delta;
        if (selectedSignal < 1)  selectedSignal = 15;
        if (selectedSignal > 15) selectedSignal = 1;
        queueScroll = 0;
        rebuildWidgets();
    }

    // ── パケット送信 ────────────────────────────────────────────

    private void testSelectedSignal() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeVarInt(selectedSignal);
        NetworkManager.sendToServer(ABSNetwork.TEST_AUDIO_CONTROLLER_SIGNAL, buf);
    }

    private void stopPlayback() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        NetworkManager.sendToServer(ABSNetwork.STOP_AUDIO_CONTROLLER_PLAYBACK, buf);
    }

    private void save() {
        List<BlockPos> parsedOffsets = parseTargetOffsets(targetPositions.getValue());
        if (parsedOffsets == null) {
            error = Component.literal("Speaker positions must be world x,y,z; x,y,z");
            return;
        }

        Map<Integer, List<String>> parsedQueues = new HashMap<>();
        for (Map.Entry<Integer, List<QueueItem>> e : queueDrafts.entrySet()) {
            List<String> refs = e.getValue().stream()
                    .filter(QueueItem::hasRef)
                    .map(QueueItem::ref)
                    .collect(Collectors.toList());
            if (!refs.isEmpty()) parsedQueues.put(e.getKey(), refs);
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeUtf(controllerId.getValue().trim(), 128);
        buf.writeEnum(redstoneMode);
        buf.writeEnum(retriggerMode);
        buf.writeVarInt(parsedOffsets.size());
        for (BlockPos offset : parsedOffsets) {
            buf.writeInt(offset.getX());
            buf.writeInt(offset.getY());
            buf.writeInt(offset.getZ());
        }
        buf.writeVarInt(parsedQueues.size());
        for (Map.Entry<Integer, List<String>> e : parsedQueues.entrySet()) {
            buf.writeVarInt(e.getKey());
            buf.writeVarInt(e.getValue().size());
            for (String ref : e.getValue()) buf.writeUtf(ref, 256);
        }

        NetworkManager.sendToServer(ABSNetwork.SAVE_AUDIO_CONTROLLER_CONFIG, buf);
        onClose();
    }

    // ── ヘルパ ──────────────────────────────────────────────────

    private String fitText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        while (!text.isEmpty() && font.width(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    private List<BlockPos> parseTargetOffsets(String raw) {
        List<BlockPos> parsed = new ArrayList<>();
        if (raw == null || raw.isBlank()) return parsed;
        for (String seg : raw.split(";")) {
            String[] parts = seg.split(",");
            if (parts.length != 3) return null;
            try {
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());
                parsed.add(new BlockPos(x - pos.getX(), y - pos.getY(), z - pos.getZ()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return parsed;
    }

    private String formatAbsoluteTargets(List<BlockPos> targets) {
        return targets.stream()
                .map(o -> (pos.getX() + o.getX()) + "," + (pos.getY() + o.getY()) + "," + (pos.getZ() + o.getZ()))
                .collect(Collectors.joining("; "));
    }

    private AudioControllerBlockEntity getController() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        BlockEntity be = mc.level.getBlockEntity(pos);
        return be instanceof AudioControllerBlockEntity c ? c : null;
    }
}
