package io.github.liquidcatmofu.abs.blockentity;

import io.github.liquidcatmofu.abs.data.AudioBounds;
import io.github.liquidcatmofu.abs.data.FalloffCurve;
import io.github.liquidcatmofu.abs.init.ABSBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SpeakerBlockEntity extends BlockEntity {
    private static final String KEY_BOUNDS = "Bounds";
    private static final String KEY_CURVE  = "FalloffCurve";

    private AudioBounds  bounds      = AudioBounds.DEFAULT;
    private FalloffCurve falloffCurve = FalloffCurve.LOGARITHMIC;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ABSBlockEntities.SPEAKER.get(), pos, state);
    }

    public AudioBounds  getBounds()      { return bounds;       }
    public FalloffCurve getFalloffCurve() { return falloffCurve; }

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
}
