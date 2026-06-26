package io.github.liquidcatmofu.abs.block;

import io.github.liquidcatmofu.abs.blockentity.AudioControllerBlockEntity;
import io.github.liquidcatmofu.abs.client.AudioBoundsSystemClient;
import io.github.liquidcatmofu.abs.init.ABSBlockEntities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class AudioControllerBlock extends BaseEntityBlock {
    public AudioControllerBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AudioControllerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            openConfigScreen(pos);
            return InteractionResult.SUCCESS;
        }
        return level.getBlockEntity(pos) instanceof AudioControllerBlockEntity
                ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof AudioControllerBlockEntity controller)) {
            return;
        }

        controller.syncRedstoneState(level.getBestNeighborSignal(pos));
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                             BlockEntityType<T> type) {
        return level.isClientSide || type != ABSBlockEntities.AUDIO_CONTROLLER.get()
                ? null
                : (lvl, pos, blockState, be) -> AudioControllerBlockEntity.serverTick(
                        lvl, pos, blockState, (AudioControllerBlockEntity) be);
    }

    @Environment(EnvType.CLIENT)
    private void openConfigScreen(BlockPos pos) {
        AudioBoundsSystemClient.openAudioControllerConfigScreen(pos);
    }
}
