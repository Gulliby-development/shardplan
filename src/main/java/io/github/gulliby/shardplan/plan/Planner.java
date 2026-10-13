package io.github.gulliby.shardplan.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** The plan rule: a deterministic greedy split in whole milliseconds. */
public final class Planner {

    private static final int MAX_SHARDS = 64;
    private static final long DEFAULT_MILLIS = 1000;

    private static final Comparator<Model.Entry> BY_PATH =
            (a, b) -> a.path().compareTo(b.path());

    private static final Comparator<Model.Entry> LONGEST_FIRST = (a, b) -> {
        int byTime = Long.compare(b.millis(), a.millis());
        return byTime != 0 ? byTime : BY_PATH.compare(a, b);
    };

    private Planner() {
    }

    /**
     * Splits the entries into exactly shardCount shards: longest first (ties by
     * path), each into the shard with the smallest total so far (ties to the
     * lowest shard number). The input list is not modified.
     */
    public static List<Model.Shard> plan(List<Model.Entry> entries, int shardCount) {
        List<Model.Entry> sorted = new ArrayList<>(entries);
        sorted.sort(LONGEST_FIRST);
        long[] totals = new long[shardCount];
        List<List<Model.Entry>> buckets = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            buckets.add(new ArrayList<>());
        }
        for (Model.Entry entry : sorted) {
            int best = 0;
            for (int i = 1; i < shardCount; i++) {
                if (totals[i] < totals[best]) {
                    best = i;
                }
            }
            totals[best] += entry.millis();
            buckets.get(best).add(entry);
        }
        List<Model.Shard> shards = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            List<Model.Entry> bucket = buckets.get(i);
            bucket.sort(BY_PATH);
            shards.add(new Model.Shard(i + 1, List.copyOf(bucket), totals[i]));
        }
        return shards;
    }

    /**
     * The smallest shard count from 1 to the smaller of 64 and the number of
     * entries whose plan has no shard longer than the target; empty otherwise.
     */
    public static OptionalInt needed(List<Model.Entry> entries, long targetMillis) {
        int limit = Math.min(MAX_SHARDS, entries.size());
        for (int n = 1; n <= limit; n++) {
            boolean fits = true;
            for (Model.Shard shard : plan(entries, n)) {
                if (shard.millis() > targetMillis) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                return OptionalInt.of(n);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * One entry per file in order: a known path takes its timing, an unknown
     * path gets the mean of the known listed timings rounded half up, or one
     * second when no listed path has a timing.
     */
    public static List<Model.Entry> estimate(List<String> files, Map<String, Long> timings) {
        long sum = 0;
        long count = 0;
        for (String file : files) {
            Long millis = timings.get(file);
            if (millis != null) {
                sum += millis;
                count++;
            }
        }
        long mean = count == 0 ? DEFAULT_MILLIS : (2 * sum + count) / (2 * count);
        List<Model.Entry> result = new ArrayList<>();
        for (String file : files) {
            Long millis = timings.get(file);
            if (millis != null) {
                result.add(new Model.Entry(file, millis, false));
            } else {
                result.add(new Model.Entry(file, mean, true));
            }
        }
        return result;
    }
}
