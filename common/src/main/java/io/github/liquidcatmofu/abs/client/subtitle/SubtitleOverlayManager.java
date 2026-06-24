package io.github.liquidcatmofu.abs.client.subtitle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

@Environment(EnvType.CLIENT)
public final class SubtitleOverlayManager {
    public static final SubtitleOverlayManager INSTANCE = new SubtitleOverlayManager();

    private String trackTitle = "";
    private String subtitle = "";
    private BlockPos sourcePos = null;
    private int ticksRemaining = 0;
    private int totalTicks = 0;

    private SubtitleOverlayManager() {
    }

    public void show(BlockPos sourcePos, String trackTitle, String subtitle, int durationTicks) {
        this.sourcePos = sourcePos;
        this.trackTitle = trackTitle == null ? "" : trackTitle.trim();
        this.subtitle = subtitle == null ? "" : subtitle.trim();
        this.totalTicks = Math.max(20, durationTicks);
        this.ticksRemaining = this.totalTicks;
    }

    public void clear() {
        trackTitle = "";
        subtitle = "";
        sourcePos = null;
        ticksRemaining = 0;
        totalTicks = 0;
    }

    public void clear(BlockPos sourcePos) {
        if (sourcePos != null && sourcePos.equals(this.sourcePos)) {
            clear();
        }
    }

    public void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
    }

    public boolean isVisible() {
        return ticksRemaining > 0 && (!trackTitle.isEmpty() || !subtitle.isEmpty());
    }

    public String getTrackTitle() {
        return trackTitle;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public float alpha() {
        if (ticksRemaining <= 0 || totalTicks <= 0) {
            return 0.0F;
        }
        int fadeTicks = Math.min(20, totalTicks / 3);
        if (ticksRemaining >= fadeTicks) {
            return 1.0F;
        }
        return ticksRemaining / (float) fadeTicks;
    }
}
