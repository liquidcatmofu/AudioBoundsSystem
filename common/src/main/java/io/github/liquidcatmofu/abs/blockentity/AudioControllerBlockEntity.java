package io.github.liquidcatmofu.abs.blockentity;

import io.github.liquidcatmofu.abs.init.ABSBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AudioControllerBlockEntity extends BlockEntity {
    public AudioControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ABSBlockEntities.AUDIO_CONTROLLER.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
    }
}
