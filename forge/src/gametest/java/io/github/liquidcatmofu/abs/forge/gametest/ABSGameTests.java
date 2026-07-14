package io.github.liquidcatmofu.abs.forge.gametest;

import io.github.liquidcatmofu.abs.AudioBoundsSystem;
import io.github.liquidcatmofu.abs.blockentity.SpeakerBlockEntity;
import io.github.liquidcatmofu.abs.blockentity.AudioControllerBlockEntity;
import io.github.liquidcatmofu.abs.data.AudioBounds;
import io.github.liquidcatmofu.abs.data.BoundsShape;
import io.github.liquidcatmofu.abs.data.ControllerRetriggerMode;
import io.github.liquidcatmofu.abs.data.FalloffCurve;
import io.github.liquidcatmofu.abs.data.RedstoneMode;
import io.github.liquidcatmofu.abs.init.ABSBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @GameTest(template = "empty")
    public static void speakerConfigurationSurvivesNbtRoundTrip(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        helper.setBlock(position, ABSBlocks.SPEAKER.get());
        SpeakerBlockEntity original = (SpeakerBlockEntity) helper.getBlockEntity(position);
        UUID owner = UUID.fromString("12345678-1234-5678-1234-567812345678");
        AudioBounds bounds = new AudioBounds(BoundsShape.BOX, 7, 12, 14, 16);
        original.applyLoadedConfig(bounds, FalloffCurve.LINEAR, RedstoneMode.PULSE,
                "abs:folder/audio/example", false, "Title", "Subtitle", "Platform Speaker");
        original.setOwnerUuid(owner);
        original.setAudioDisplayName("Example Audio");

        SpeakerBlockEntity restored = new SpeakerBlockEntity(
                helper.absolutePos(position), original.getBlockState());
        restored.load(original.getUpdateTag());

        helper.assertTrue(restored.getBounds().getShape() == BoundsShape.BOX, "Bounds shape was not restored");
        helper.assertTrue(restored.getBounds().getWidth() == 12, "Bounds width was not restored");
        helper.assertTrue(restored.getFalloffCurve() == FalloffCurve.LINEAR, "Falloff curve was not restored");
        helper.assertTrue(restored.getRedstoneMode() == RedstoneMode.PULSE, "Redstone mode was not restored");
        helper.assertTrue(restored.getAudioFile().equals("abs:folder/audio/example"), "Audio ref was not restored");
        helper.assertTrue(!restored.isSubtitleEnabled(), "Subtitle setting was not restored");
        helper.assertTrue(restored.getTrackTitle().equals("Title"), "Track title was not restored");
        helper.assertTrue(restored.getSubtitle().equals("Subtitle"), "Subtitle was not restored");
        helper.assertTrue(restored.getDisplayName().equals("Platform Speaker"), "Display name was not restored");
        helper.assertTrue(restored.getAudioDisplayName().equals("Example Audio"), "Audio name was not restored");
        helper.assertTrue(owner.equals(restored.getOwnerUuid()), "Owner was not restored");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void controllerConfigurationSurvivesNbtRoundTrip(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        helper.setBlock(position, ABSBlocks.AUDIO_CONTROLLER.get());
        AudioControllerBlockEntity original = (AudioControllerBlockEntity) helper.getBlockEntity(position);
        UUID owner = UUID.fromString("87654321-4321-8765-4321-876543218765");
        List<BlockPos> targets = List.of(new BlockPos(2, 0, 0), new BlockPos(-2, 1, 3));
        Map<Integer, List<String>> queues = Map.of(
                1, List.of("abs:first"),
                15, List.of("abs:second", "abs:third"));
        original.applyLoadedConfig("station-controller", targets, queues,
                RedstoneMode.LEVEL, ControllerRetriggerMode.STOP);
        original.setOwnerUuid(owner);

        AudioControllerBlockEntity restored = new AudioControllerBlockEntity(
                helper.absolutePos(position), original.getBlockState());
        restored.load(original.getUpdateTag());

        helper.assertTrue(restored.getControllerId().equals("station-controller"), "Controller ID was not restored");
        helper.assertTrue(restored.getTargetSpeakerOffsets().equals(targets), "Speaker targets were not restored");
        helper.assertTrue(restored.getRedstoneQueues().equals(queues), "Redstone queues were not restored");
        helper.assertTrue(restored.getRedstoneMode() == RedstoneMode.LEVEL, "Redstone mode was not restored");
        helper.assertTrue(restored.getRetriggerMode() == ControllerRetriggerMode.STOP,
                "Retrigger mode was not restored");
        helper.assertTrue(owner.equals(restored.getOwnerUuid()), "Owner was not restored");
        helper.succeed();
    }
}
