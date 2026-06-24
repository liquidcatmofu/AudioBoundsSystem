package io.github.liquidcatmofu.abs.client.subtitle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class SubtitleHudRenderer {
    private static final int MAX_WIDTH = 320;
    private static final int PADDING_X = 8;
    private static final int PADDING_Y = 5;
    private static final int LINE_HEIGHT = 10;

    private SubtitleHudRenderer() {
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        SubtitleOverlayManager overlay = SubtitleOverlayManager.INSTANCE;
        if (!overlay.isVisible()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int contentWidth = Math.min(MAX_WIDTH, screenWidth - 32);

        List<FormattedCharSequence> subtitleLines = font.split(Component.literal(overlay.getSubtitle()), contentWidth);
        boolean hasTitle = !overlay.getTrackTitle().isEmpty();
        boolean hasSubtitle = !overlay.getSubtitle().isEmpty();
        int textHeight = (hasTitle ? LINE_HEIGHT : 0) + (hasTitle && hasSubtitle ? 2 : 0) + subtitleLines.size() * LINE_HEIGHT;
        int panelWidth = panelWidth(font, overlay.getTrackTitle(), subtitleLines, contentWidth) + PADDING_X * 2;
        int panelHeight = textHeight + PADDING_Y * 2;
        int left = (screenWidth - panelWidth) / 2;
        int top = screenHeight - panelHeight - 54;

        int alpha = Math.max(0, Math.min(255, (int) (overlay.alpha() * 255.0F)));
        int titleColor = (alpha << 24) | 0x80D8FF;
        int subtitleColor = (alpha << 24) | 0xFFFFFF;

        int y = top + PADDING_Y;
        if (hasTitle) {
            graphics.drawCenteredString(font, Component.literal(overlay.getTrackTitle()), screenWidth / 2, y, titleColor);
            y += LINE_HEIGHT + (hasSubtitle ? 2 : 0);
        }
        for (FormattedCharSequence line : subtitleLines) {
            graphics.drawString(font, line, (screenWidth - font.width(line)) / 2, y, subtitleColor);
            y += LINE_HEIGHT;
        }
    }

    private static int panelWidth(Font font, String trackTitle, List<FormattedCharSequence> subtitleLines, int fallback) {
        int width = trackTitle.isEmpty() ? 0 : font.width(trackTitle);
        for (FormattedCharSequence line : subtitleLines) {
            width = Math.max(width, font.width(line));
        }
        return Math.max(48, Math.min(fallback, width));
    }
}
