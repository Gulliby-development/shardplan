package io.github.gulliby.shardplan.plan;

import java.util.List;

/** The data types of a plan. */
public final class Model {

    private Model() {
    }

    /** One test file with its time in whole milliseconds. */
    public record Entry(String path, long millis, boolean estimated) {
    }

    /**
     * One shard: index from 1, entries in ascending ordinal order of path,
     * millis the sum of the entries.
     */
    public record Shard(int index, List<Model.Entry> entries, long millis) {
    }
}
