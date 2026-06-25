package io.github.liquidcatmofu.abs.client.gui;

import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import io.github.liquidcatmofu.abs.data.AudioBounds;
import io.github.liquidcatmofu.abs.data.BoundsShape;
import io.github.liquidcatmofu.abs.data.FalloffCurve;
import io.github.liquidcatmofu.abs.data.RedstoneMode;
import io.github.liquidcatmofu.abs.network.ABSNetwork;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class SpeakerConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int ROW_HEIGHT = 24;
    private static final int CONTENT_HEIGHT = ROW_HEIGHT * 13 + 34;
    private static final int SCREEN_MARGIN = 12;

    private final BlockPos pos;
    private final Screen parent;
    private final List<PositionedWidget> positionedWidgets = new ArrayList<>();
    private BoundsShape shape;
    private FalloffCurve falloffCurve;
    private RedstoneMode redstoneMode;
    private boolean subtitleEnabled;
    private int scrollOffset = 0;
    private EditBox radius;
    private EditBox widthValue;
    private EditBox depth;
    private EditBox heightValue;
    private EditBox audioFile;
    private EditBox trackTitle;
    private EditBox subtitle;
    private Component error = Component.empty();

    public SpeakerConfigScreen(BlockPos pos, Screen parent) {
        super(Component.literal("ABS - Speaker Area"));
        this.pos = pos;
        this.parent = parent;
        SpeakerBlockEntity speaker = currentSpeaker();
        AudioBounds bounds = speaker == null ? AudioBounds.DEFAULT : speaker.getBounds();
        this.shape = bounds.getShape();
        this.falloffCurve = speaker == null ? FalloffCurve.LOGARITHMIC : speaker.getFalloffCurve();
        this.redstoneMode = speaker == null ? RedstoneMode.LEVEL : speaker.getRedstoneMode();
        this.subtitleEnabled = speaker == null || speaker.isSubtitleEnabled();
    }

    @Override
    protected void init() {
        positionedWidgets.clear();
        scrollOffset = Math.min(scrollOffset, maxScroll());
        int x = (this.width - PANEL_WIDTH) / 2;
        int y = contentTop();
        SpeakerBlockEntity speaker = currentSpeaker();
        AudioBounds bounds = speaker == null ? AudioBounds.DEFAULT : speaker.getBounds();

        addPositionedWidget(CycleButton.<BoundsShape>builder(value -> Component.literal(value.name()))
                .withValues(BoundsShape.values())
                .withInitialValue(shape)
                .create(x, y + ROW_HEIGHT, PANEL_WIDTH, 20, Component.literal("Shape"), (button, value) -> shape = value), ROW_HEIGHT);
        radius = addEditBox(x + 110, y + ROW_HEIGHT * 2, 90, "Radius", bounds.getRadius());
        widthValue = addEditBox(x + 110, y + ROW_HEIGHT * 3, 90, "Width", bounds.getWidth());
        heightValue = addEditBox(x + 110, y + ROW_HEIGHT * 4, 90, "Height", bounds.getHeight());
        depth = addEditBox(x + 110, y + ROW_HEIGHT * 5, 90, "Depth", bounds.getDepth());

        addPositionedWidget(CycleButton.<FalloffCurve>builder(value -> Component.literal(value.name()))
                .withValues(FalloffCurve.values())
                .withInitialValue(falloffCurve)
                .create(x, y + ROW_HEIGHT * 6, PANEL_WIDTH, 20, Component.literal("Falloff"), (button, value) -> falloffCurve = value), ROW_HEIGHT * 6);

        addPositionedWidget(CycleButton.<RedstoneMode>builder(value -> Component.literal(value.name()))
                .withValues(RedstoneMode.values())
                .withInitialValue(redstoneMode)
                .create(x, y + ROW_HEIGHT * 7, PANEL_WIDTH, 20, Component.literal("Redstone"), (button, value) -> redstoneMode = value), ROW_HEIGHT * 7);

        audioFile = new EditBox(this.font, x + 110, y + ROW_HEIGHT * 8, PANEL_WIDTH - 110, 20, Component.literal("Audio file"));
        audioFile.setMaxLength(256);
        audioFile.setValue(speaker == null ? "" : speaker.getAudioFile());
        addPositionedWidget(audioFile, ROW_HEIGHT * 8);

        addPositionedWidget(Button.builder(subtitleEnabledLabel(), button -> {
                    subtitleEnabled = !subtitleEnabled;
                    button.setMessage(subtitleEnabledLabel());
                })
                .bounds(x, y + ROW_HEIGHT * 9, PANEL_WIDTH, 20)
                .build(), ROW_HEIGHT * 9);

        trackTitle = new EditBox(this.font, x + 110, y + ROW_HEIGHT * 10, PANEL_WIDTH - 110, 20, Component.literal("Track title"));
        trackTitle.setMaxLength(128);
        trackTitle.setValue(speaker == null ? "" : speaker.getTrackTitle());
        addPositionedWidget(trackTitle, ROW_HEIGHT * 10);

        subtitle = new EditBox(this.font, x + 110, y + ROW_HEIGHT * 11, PANEL_WIDTH - 110, 20, Component.literal("Subtitle"));
        subtitle.setMaxLength(512);
        subtitle.setValue(speaker == null ? "" : speaker.getSubtitle());
        addPositionedWidget(subtitle, ROW_HEIGHT * 11);

        addPositionedWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(x + PANEL_WIDTH - 154, y + ROW_HEIGHT * 11 + 10, 74, 20)
                .build(), ROW_HEIGHT * 11 + 10);
        addPositionedWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(x + PANEL_WIDTH - 74, y + ROW_HEIGHT * 11 + 10, 74, 20)
                .build(), ROW_HEIGHT * 11 + 10);
        updateWidgetPositions();
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
        drawLabel(graphics, "Audio file", x, y + ROW_HEIGHT * 8 + 6);
        drawLabel(graphics, "Track title", x, y + ROW_HEIGHT * 10 + 6);
        drawLabel(graphics, "Subtitle", x, y + ROW_HEIGHT * 11 + 6);
        if (error != Component.empty()) {
            graphics.drawString(this.font, error, x, y + ROW_HEIGHT * 12 - 2, 0xFF6666);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int previous = scrollOffset;
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int) Math.signum(delta) * ROW_HEIGHT));
        if (scrollOffset != previous) {
            updateWidgetPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
        buf.writeEnum(redstoneMode);
        buf.writeUtf(audioFile.getValue(), 256);
        buf.writeBoolean(subtitleEnabled);
        buf.writeUtf(trackTitle.getValue(), 128);
        buf.writeUtf(subtitle.getValue(), 512);
        NetworkManager.sendToServer(ABSNetwork.SAVE_SPEAKER_CONFIG, buf);
        onClose();
    }

    private EditBox addEditBox(int x, int y, int editWidth, String name, double value) {
        EditBox box = new EditBox(this.font, x, y, editWidth, 20, Component.literal(name));
        box.setMaxLength(16);
        box.setValue(Double.toString(value));
        addPositionedWidget(box, y - contentTop());
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

    private Component subtitleEnabledLabel() {
        return Component.literal("Subtitles: " + (subtitleEnabled ? "On" : "Off"));
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
        return SCREEN_MARGIN - scrollOffset;
    }

    private int maxScroll() {
        return Math.max(0, CONTENT_HEIGHT + SCREEN_MARGIN * 2 - this.height);
    }

    private <T extends AbstractWidget> T addPositionedWidget(T widget, int baseY) {
        positionedWidgets.add(new PositionedWidget(widget, baseY));
        return addRenderableWidget(widget);
    }

    private void updateWidgetPositions() {
        int top = contentTop();
        for (PositionedWidget positionedWidget : positionedWidgets) {
            positionedWidget.widget.setY(top + positionedWidget.baseY);
        }
    }

    private void drawLabel(GuiGraphics graphics, String label, int x, int y) {
        graphics.drawString(this.font, Component.literal(label), x, y, 0xA0A0A0);
    }

    private record PositionedWidget(AbstractWidget widget, int baseY) {
    }
}
