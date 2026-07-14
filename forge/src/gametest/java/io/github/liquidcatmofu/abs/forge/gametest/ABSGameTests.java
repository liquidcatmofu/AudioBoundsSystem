package io.github.liquidcatmofu.abs.forge.gametest;

import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import io.github.liquidcatmofu.abs.init.ABSBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AudioBoundsSystem.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ABSGameTests {
    private ABSGameTests() {}

    @GameTest(template = "empty")
    public static void speakerBlockCreatesItsBlockEntity(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        helper.setBlock(position, ABSBlocks.SPEAKER.get());

        helper.assertTrue(helper.getBlockEntity(position) instanceof SpeakerBlockEntity,
                "Speaker block did not create a SpeakerBlockEntity");
        helper.succeed();
    }
}
