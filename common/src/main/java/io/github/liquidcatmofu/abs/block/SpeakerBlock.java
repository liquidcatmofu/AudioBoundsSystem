package io.github.liquidcatmofu.abs.block;

import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class SpeakerBlock extends BaseEntityBlock {
    public SpeakerBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
