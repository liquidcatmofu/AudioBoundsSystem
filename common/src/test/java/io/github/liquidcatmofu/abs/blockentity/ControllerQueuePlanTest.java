package io.github.liquidcatmofu.abs.blockentity;

import io.github.liquidcatmofu.abs.library.SequenceEntry;
import io.github.liquidcatmofu.abs.library.SequenceStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerQueuePlanTest {
    @Test
    void preservesOrdinaryRefsAndExpandsSequenceStepsInOrder() {
        SequenceEntry sequence = sequence(
                step("lib:music/audio/intro", 4),
                step("lib:music/tts/message", 10));

        List<ControllerQueuePlan.Step> plan = ControllerQueuePlan.expand(
                List.of("lib:music/audio/before", "lib:music/sequence/show", "lib:music/audio/after"),
                (folderId, sequenceId) -> {
                    assertEquals("music", folderId);
                    assertEquals("show", sequenceId);
                    return Optional.of(sequence);
                });

        assertEquals(List.of(
                new ControllerQueuePlan.Step("lib:music/audio/before", 0),
                new ControllerQueuePlan.Step("lib:music/audio/intro", 4),
                new ControllerQueuePlan.Step("lib:music/tts/message", 10),
                new ControllerQueuePlan.Step("lib:music/audio/after", 0)), plan);
    }

    @Test
    void omitsMissingSequencesInsteadOfTreatingThemAsAudio() {
        List<ControllerQueuePlan.Step> plan = ControllerQueuePlan.expand(
                List.of("lib:music/sequence/missing", "lib:music/audio/available"),
                (folderId, sequenceId) -> Optional.empty());

        assertEquals(List.of(new ControllerQueuePlan.Step("lib:music/audio/available", 0)), plan);
    }

    @Test
    void skipsBlankSequenceStepsAndClampsNegativeDelays() {
        SequenceEntry sequence = sequence(
                step("", 12),
                step("lib:music/audio/valid", -5),
                null);

        List<ControllerQueuePlan.Step> plan = ControllerQueuePlan.expand(
                List.of("lib:music/sequence/show"),
                (folderId, sequenceId) -> Optional.of(sequence));

        assertEquals(List.of(new ControllerQueuePlan.Step("lib:music/audio/valid", 0)), plan);
    }

    @Test
    void toleratesSequenceMetadataWithoutSteps() {
        SequenceEntry sequence = new SequenceEntry();
        sequence.steps = null;

        assertEquals(List.of(), ControllerQueuePlan.expand(
                List.of("lib:music/sequence/empty"),
                (folderId, sequenceId) -> Optional.of(sequence)));
    }

    private static SequenceEntry sequence(SequenceStep... steps) {
        SequenceEntry sequence = new SequenceEntry();
        sequence.steps = java.util.Arrays.asList(steps);
        return sequence;
    }

    private static SequenceStep step(String audioRef, int delayTicks) {
        SequenceStep step = new SequenceStep();
        step.audioRef = audioRef;
        step.delayTicks = delayTicks;
        return step;
    }
}
