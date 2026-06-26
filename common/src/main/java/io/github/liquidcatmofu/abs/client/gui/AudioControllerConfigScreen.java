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

@Environment(EnvType.CLIENT)
public final class AudioControllerConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int LABEL_X = 20;

    private final BlockPos pos;
    private final Screen parent;
    private EditBox controllerId;
    private EditBox targetPositions;
    private EditBox queueEntries;
    private Component error;
    private int selectedSignal = 15;
    private RedstoneMode redstoneMode = RedstoneMode.PULSE;
    private ControllerRetriggerMode retriggerMode = ControllerRetriggerMode.RESTART;
    private final Map<Integer, String> queueDrafts = new HashMap<>();

    public AudioControllerConfigScreen(BlockPos pos, Screen parent) {
        super(Component.literal("ABS Audio Controller"));
        this.pos = pos;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = 16;

        AudioControllerBlockEntity controller = getController();
        String initialControllerId = controller == null ? "" : controller.getControllerId();
        String initialTargets = controller == null ? "" : formatAbsoluteTargets(controller.getTargetSpeakerOffsets());
        if (controller != null) {
            redstoneMode = controller.getRedstoneMode();
            retriggerMode = controller.getRetriggerMode();
            for (Map.Entry<Integer, List<String>> entry : controller.getRedstoneQueues().entrySet()) {
                queueDrafts.put(entry.getKey(), String.join(", ", entry.getValue()));
            }
        }

        controllerId = new EditBox(font, left, top + 12, PANEL_WIDTH, 20, Component.literal("Controller ID"));
        controllerId.setMaxLength(128);
        controllerId.setValue(initialControllerId);
        addRenderableWidget(controllerId);

        targetPositions = new EditBox(font, left, top + 52, PANEL_WIDTH, 20, Component.literal("Speaker positions"));
        targetPositions.setMaxLength(1024);
        targetPositions.setValue(initialTargets);
        addRenderableWidget(targetPositions);

        addRenderableWidget(CycleButton.<RedstoneMode>builder(value -> Component.literal(value.name()))
                .withValues(RedstoneMode.values())
                .withInitialValue(redstoneMode)
                .create(left, top + 84, PANEL_WIDTH, 20, Component.literal("Redstone Mode"), (button, value) -> redstoneMode = value));

        addRenderableWidget(CycleButton.<ControllerRetriggerMode>builder(value -> Component.literal(value.name()))
                .withValues(ControllerRetriggerMode.values())
                .withInitialValue(retriggerMode)
                .create(left, top + 108, PANEL_WIDTH, 20, Component.literal("Retrigger Mode"), (button, value) -> retriggerMode = value));

        addRenderableWidget(Button.builder(Component.literal("<"), button -> changeSignal(-1))
                .bounds(left, top + 148, 24, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> changeSignal(1))
                .bounds(left + PANEL_WIDTH - 24, top + 148, 24, 20)
                .build());

        queueEntries = new EditBox(font, left + 32, top + 148, PANEL_WIDTH - 64, 20, Component.literal("Queue"));
        queueEntries.setMaxLength(1024);
        queueEntries.setValue(queueDrafts.getOrDefault(selectedSignal, ""));
        addRenderableWidget(queueEntries);

        addRenderableWidget(Button.builder(Component.literal("Play Test"), button -> testSelectedSignal())
                .bounds(left, top + 188, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Stop"), button -> stopPlayback())
                .bounds(left + 86, top + 188, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(left + 172, top + 188, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(left + 258, top + 188, 82, 20)
                .build());

        setInitialFocus(controllerId);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top = 16;
        graphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        graphics.drawString(font, "Controller ID", left + LABEL_X, top, 0xA0A0A0, false);
        graphics.drawString(font, "Speaker positions (world x,y,z; x,y,z)", left + LABEL_X, top + 40, 0xA0A0A0, false);
        graphics.drawString(font, "Signal " + selectedSignal + " Queue", left + LABEL_X, top + 136, 0xA0A0A0, false);
        graphics.drawString(font, "Files: a.ogg, b.ogg", left + LABEL_X, top + 172, 0x808080, false);
        if (error != null) {
            graphics.drawCenteredString(font, error, width / 2, top + 216, 0xFF8080);
        }
    }

    private void changeSignal(int delta) {
        queueDrafts.put(selectedSignal, queueEntries.getValue().trim());
        selectedSignal += delta;
        if (selectedSignal < 1) {
            selectedSignal = 15;
        } else if (selectedSignal > 15) {
            selectedSignal = 1;
        }
        queueEntries.setValue(queueDrafts.getOrDefault(selectedSignal, ""));
    }

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
        queueDrafts.put(selectedSignal, queueEntries.getValue().trim());

        List<BlockPos> parsedOffsets = parseTargetOffsets(targetPositions.getValue());
        if (parsedOffsets == null) {
            error = Component.literal("Speaker positions must be world x,y,z; x,y,z");
            return;
        }

        Map<Integer, List<String>> parsedQueues = parseQueues();
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
        for (Map.Entry<Integer, List<String>> entry : parsedQueues.entrySet()) {
            buf.writeVarInt(entry.getKey());
            buf.writeVarInt(entry.getValue().size());
            for (String audioFile : entry.getValue()) {
                buf.writeUtf(audioFile, 256);
            }
        }

        NetworkManager.sendToServer(ABSNetwork.SAVE_AUDIO_CONTROLLER_CONFIG, buf);
        onClose();
    }

    private Map<Integer, List<String>> parseQueues() {
        Map<Integer, List<String>> parsed = new HashMap<>();
        for (Map.Entry<Integer, String> entry : queueDrafts.entrySet()) {
            String rawValue = entry.getValue();
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }

            List<String> queue = new ArrayList<>();
            for (String token : rawValue.split(",")) {
                String value = token.trim();
                if (!value.isEmpty()) {
                    queue.add(value);
                }
            }

            if (!queue.isEmpty()) {
                parsed.put(entry.getKey(), queue);
            }
        }
        return parsed;
    }

    private List<BlockPos> parseTargetOffsets(String rawTargets) {
        List<BlockPos> parsed = new ArrayList<>();
        if (rawTargets == null || rawTargets.isBlank()) {
            return parsed;
        }

        for (String value : rawTargets.split(";")) {
            String[] parts = value.split(",");
            if (parts.length != 3) {
                return null;
            }

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
        List<String> entries = new ArrayList<>();
        for (BlockPos targetOffset : targets) {
            BlockPos absolutePos = pos.offset(targetOffset);
            entries.add(absolutePos.getX() + "," + absolutePos.getY() + "," + absolutePos.getZ());
        }
        return String.join("; ", entries);
    }

    private AudioControllerBlockEntity getController() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
        return blockEntity instanceof AudioControllerBlockEntity controller ? controller : null;
    }
}
