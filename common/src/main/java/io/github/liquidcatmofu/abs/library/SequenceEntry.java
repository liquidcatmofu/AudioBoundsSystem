package io.github.liquidcatmofu.abs.library;

import java.util.ArrayList;
import java.util.List;

public class SequenceEntry {
    public String            id;
    public String            displayName;
    public List<SequenceStep> steps = new ArrayList<>();
    public String            createdBy;
    public long              createdAt;
    public long              updatedAt;
}
