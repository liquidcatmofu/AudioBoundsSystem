package io.github.liquidcatmofu.abs.blockentity;

import io.github.liquidcatmofu.abs.library.LibraryRef;
import io.github.liquidcatmofu.abs.library.SequenceEntry;
import io.github.liquidcatmofu.abs.library.SequenceStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Expands configured controller refs into immutable playback steps. */
final class ControllerQueuePlan {
    record Step(String audioRef, int delayAfterTicks) {}

    @FunctionalInterface
    interface SequenceLoader {
        Optional<SequenceEntry> load(String folderId, String sequenceId);
    }

    private ControllerQueuePlan() {}

    static List<Step> expand(List<String> refs, SequenceLoader sequenceLoader) {
        List<Step> result = new ArrayList<>();
        for (String ref : refs) {
            String[] sequenceRef = parseSequenceRef(ref);
            if (sequenceRef == null) {
                result.add(new Step(ref, 0));
                continue;
            }

            sequenceLoader.load(sequenceRef[0], sequenceRef[1]).ifPresent(sequence -> addSequenceSteps(result, sequence));
        }
        return List.copyOf(result);
    }

    private static void addSequenceSteps(List<Step> result, SequenceEntry sequence) {
        if (sequence.steps == null) {
            return;
        }
        for (SequenceStep step : sequence.steps) {
            if (step != null && step.audioRef != null && !step.audioRef.isBlank()) {
                result.add(new Step(step.audioRef, Math.max(0, step.delayTicks)));
            }
        }
    }

    private static String[] parseSequenceRef(String ref) {
        if (ref == null || !ref.startsWith(LibraryRef.PREFIX)) {
            return null;
        }
        String[] parts = ref.substring(LibraryRef.PREFIX.length()).split("/", 3);
        if (parts.length != 3 || !"sequence".equals(parts[1])) {
            return null;
        }
        return new String[]{parts[0], parts[2]};
    }
}
