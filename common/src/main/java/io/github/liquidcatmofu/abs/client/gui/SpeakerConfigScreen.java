package io.github.liquidcatmofu.abs.client.gui;

import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import io.github.liquidcatmofu.abs.data.AudioBounds;
import io.github.liquidcatmofu.abs.data.BoundsShape;
import io.github.liquidcatmofu.abs.data.FalloffCurve;
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

@Environment(EnvType.CLIENT)
public final class SpeakerConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int ROW_HEIGHT = 24;
    private static final int CONTENT_HEIGHT = ROW_HEIGHT * 9 + 34;
    private static final int SCREEN_MARGIN = 12;

    private final BlockPos pos;
    private final Screen parent;
    private BoundsShape shape;
    private FalloffCurve falloffCurve;
    private EditBox radius;
    private EditBox widthValue;
    private EditBox depth;
    private EditBox heightValue;
    private EditBox audioFile;
    private Component error = Component.empty();

    public SpeakerConfigScreen(BlockPos pos, Screen parent) {
        super(Component.literal("ABS - Speaker Area"));
        this.pos = pos;
        this.parent = parent;
        SpeakerBlockEntity speaker = currentSpeaker();
        AudioBounds bounds = speaker == null ? AudioBounds.DEFAULT : speaker.getBounds();
        this.shape = bounds.getShape();
        this.falloffCurve = speaker == null ? FalloffCurve.LOGARITHMIC : speaker.getFalloffCurve();
    }

    @Override
    protected void init() {
        int x = (this.width - PANEL_WIDTH) / 2;
        int y = contentTop();
        SpeakerBlockEntity speaker = currentSpeaker();
        AudioBounds bounds = speaker == null ? AudioBounds.DEFAULT : speaker.getBounds();

        addRenderableWidget(CycleButton.<BoundsShape>builder(value -> Component.literal(value.name()))
                .withValues(BoundsShape.values())
                .withInitialValue(shape)
                .create(x, y + ROW_HEIGHT, PANEL_WIDTH, 20, Component.literal("Shape"), (button, value) -> shape = value));
        radius = addEditBox(x + 110, y + ROW_HEIGHT * 2, 90, "Radius", bounds.getRadius());
        widthValue = addEditBox(x + 110, y + ROW_HEIGHT * 3, 90, "Width", bounds.getWidth());
        heightValue = addEditBox(x + 110, y + ROW_HEIGHT * 4, 90, "Height", bounds.getHeight());
        depth = addEditBox(x + 110, y + ROW_HEIGHT * 5, 90, "Depth", bounds.getDepth());

        addRenderableWidget(CycleButton.<FalloffCurve>builder(value -> Component.literal(value.name()))
                .withValues(FalloffCurve.values())
                .withInitialValue(falloffCurve)
                .create(x, y + ROW_HEIGHT * 6, PANEL_WIDTH, 20, Component.literal("Falloff"), (button, value) -> falloffCurve = value));

        audioFile = new EditBox(this.font, x + 110, y + ROW_HEIGHT * 7, PANEL_WIDTH - 110, 20, Component.literal("Audio file"));
        audioFile.setMaxLength(256);
        audioFile.setValue(speaker == null ? "" : speaker.getAudioFile());
        addRenderableWidget(audioFile);

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(x + PANEL_WIDTH - 154, y + ROW_HEIGHT * 8 + 10, 74, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(x + PANEL_WIDTH - 74, y + ROW_HEIGHT * 8 + 10, 74, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int x = (this.width - PANEL_WIDTH) / 2;
        int y = contentTop();
        graphics.drawCenteredString(this.font, this.title, this.width / 2, y, 0xFFFFFF);
        drawLabel(graphics, "Radius", x, y + ROW_HEIGHT * 2 + 6);
        drawLabel(graphics, "Width", x, y + ROW_HEIGHT * 3 + 6);
        drawLabel(graphics, "Height", x, y + ROW_HEIGHT * 4 + 6);
        drawLabel(graphics, "Depth", x, y + ROW_HEIGHT * 5 + 6);
        drawLabel(graphics, "Audio file", x, y + ROW_HEIGHT * 7 + 6);
        if (error != Component.empty()) {
            graphics.drawString(this.font, error, x, y + ROW_HEIGHT * 8 - 2, 0xFF6666);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void save() {
        double parsedRadius = parsePositive(radius);
        double parsedWidth = parsePositive(widthValue);
        double parsedDepth = parsePositive(depth);
        double parsedHeight = parsePositive(heightValue);
        if (Double.isNaN(parsedRadius) || Double.isNaN(parsedWidth) || Double.isNaN(parsedDepth) || Double.isNaN(parsedHeight)) {
            error = Component.literal("Use positive numeric values.");
            return;
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeEnum(shape);
        buf.writeDouble(parsedRadius);
        buf.writeDouble(parsedWidth);
        buf.writeDouble(parsedDepth);
        buf.writeDouble(parsedHeight);
        buf.writeEnum(falloffCurve);
        buf.writeUtf(audioFile.getValue(), 256);
        NetworkManager.sendToServer(ABSNetwork.SAVE_SPEAKER_CONFIG, buf);
        onClose();
    }

    private EditBox addEditBox(int x, int y, int editWidth, String name, double value) {
        EditBox box = new EditBox(this.font, x, y, editWidth, 20, Component.literal(name));
        box.setMaxLength(16);
        box.setValue(Double.toString(value));
        addRenderableWidget(box);
        return box;
    }

    private double parsePositive(EditBox box) {
        try {
            double value = Double.parseDouble(box.getValue());
            return value > 0.0D ? value : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private SpeakerBlockEntity currentSpeaker() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
        return blockEntity instanceof SpeakerBlockEntity speaker ? speaker : null;
    }

    private int contentTop() {
        int centered = (this.height - CONTENT_HEIGHT) / 2;
        int maxTop = this.height - CONTENT_HEIGHT - SCREEN_MARGIN;
        return Math.max(SCREEN_MARGIN, Math.min(centered, maxTop));
    }

    private void drawLabel(GuiGraphics graphics, String label, int x, int y) {
        graphics.drawString(this.font, Component.literal(label), x, y, 0xA0A0A0);
    }
}
