package io.github.liquidcatmofu.abs.block;

import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import io.github.liquidcatmofu.abs.client.AudioBoundsSystemClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            if (!player.isShiftKeyDown()) {
                openConfigScreen(pos);
            }
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof SpeakerBlockEntity be)) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) return InteractionResult.CONSUME;

        if (be.isPlaying()) {
            be.stopPlaying((ServerLevel) level);
        } else {
            be.startPlaying((ServerLevel) level);
        }
        return InteractionResult.CONSUME;
    }

    @Environment(EnvType.CLIENT)
    private void openConfigScreen(BlockPos pos) {
        AudioBoundsSystemClient.openSpeakerConfigScreen(pos);
    }
}
