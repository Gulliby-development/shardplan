package io.github.gulliby.shardplan.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class PlannerTest {

    private static Model.Entry entry(String path, long millis) {
        return new Model.Entry(path, millis, false);
    }

    private static List<Model.Entry> worked() {
        List<Model.Entry> entries = new ArrayList<>();
        entries.add(entry("tests/test_db.py", 42500));
        entries.add(entry("tests/test_cli.py", 3200));
        entries.add(entry("tests/test_api.py", 18000));
        entries.add(entry("tests/test_parser.py", 27250));
        entries.add(entry("tests/test_utils.py", 800));
        entries.add(entry("tests/test_auth.py", 9100));
        return entries;
    }

    private static List<Model.Entry> same(int count, long millis) {
        List<Model.Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(entry("f" + i, millis));
        }
        return entries;
    }

    @Test
    void parseAcceptsValidTexts() {
        String[] texts = {"12", "0.5", "3.250", "99999.999", "0", "0.001", "00012.5",
            "99999", "1.25", "0.000", "7.05"};
        long[] millis = {12000, 500, 3250, 99999999, 0, 1, 12500, 99999000, 1250, 0, 7050};
        assertEquals(texts.length, millis.length);
        for (int i = 0; i < texts.length; i++) {
            assertEquals(OptionalLong.of(millis[i]), Seconds.parse(texts[i]), texts[i]);
        }
    }

    @Test
    void parseRejectsInvalidTexts() {
        String[] texts = {"", ".5", "5.", "1.2345", "123456", "+1", "-1", "1e3", "1,5",
            " 1", "1 ", "x", "1..2", "\u0661", "1.\u0661", "123456.5", "1.2x", "1.2.3", ".",
            "1x", "1.5 "};
        for (String text : texts) {
            assertEquals(OptionalLong.empty(), Seconds.parse(text), "[" + text + "]");
        }
    }

    @Test
    void formatWritesSecondsWithThreeDigits() {
        assertEquals("0.000", Seconds.format(0));
        assertEquals("0.005", Seconds.format(5));
        assertEquals("0.800", Seconds.format(800));
        assertEquals("42.500", Seconds.format(42500));
        assertEquals("99999.999", Seconds.format(99999999));
        assertEquals("199999998.000", Seconds.format(199999998000L));
    }

    @Test
    void formatPadsAtTheDigitBoundaries() {
        assertEquals("0.009", Seconds.format(9));
        assertEquals("0.010", Seconds.format(10));
        assertEquals("0.099", Seconds.format(99));
        assertEquals("0.100", Seconds.format(100));
        assertEquals("0.999", Seconds.format(999));
        assertEquals("1.000", Seconds.format(1000));
        assertEquals("1.001", Seconds.format(1001));
    }

    @Test
    void planSplitsTheWorkedExampleIntoThreeShards() {
        List<Model.Entry> entries = worked();
        List<Model.Entry> before = new ArrayList<>(entries);

        List<Model.Shard> shards = Planner.plan(entries, 3);

        assertEquals(3, shards.size());
        assertEquals(new Model.Shard(1, List.of(entry("tests/test_db.py", 42500)), 42500),
                shards.get(0));
        assertEquals(new Model.Shard(2, List.of(entry("tests/test_parser.py", 27250),
                entry("tests/test_utils.py", 800)), 28050), shards.get(1));
        assertEquals(new Model.Shard(3, List.of(entry("tests/test_api.py", 18000),
                entry("tests/test_auth.py", 9100), entry("tests/test_cli.py", 3200)), 30300),
                shards.get(2));
        assertEquals(before, entries);
        assertEquals("tests/test_db.py", entries.get(0).path());
        assertEquals("tests/test_auth.py", entries.get(5).path());
    }

    @Test
    void planBreaksTiesByPathAndByLowestShard() {
        List<Model.Entry> entries = List.of(entry("c", 1000), entry("b", 1000), entry("a", 1000));

        List<Model.Shard> shards = Planner.plan(entries, 2);

        assertEquals(2, shards.size());
        assertEquals(new Model.Shard(1, List.of(entry("a", 1000), entry("c", 1000)), 2000),
                shards.get(0));
        assertEquals(new Model.Shard(2, List.of(entry("b", 1000)), 1000), shards.get(1));
    }

    @Test
    void planPutsTheLongestFirstAndSortsEachShardByPath() {
        List<Model.Entry> entries = List.of(entry("a", 1000), entry("z", 5000), entry("m", 1000));

        List<Model.Shard> shards = Planner.plan(entries, 2);

        assertEquals(new Model.Shard(1, List.of(entry("z", 5000)), 5000), shards.get(0));
        assertEquals(new Model.Shard(2, List.of(entry("a", 1000), entry("m", 1000)), 2000),
                shards.get(1));
    }

    @Test
    void planWithOneShardHoldsEveryFileInPathOrder() {
        List<Model.Shard> shards = Planner.plan(worked(), 1);

        assertEquals(1, shards.size());
        assertEquals(1, shards.get(0).index());
        assertEquals(100850, shards.get(0).millis());
        List<String> paths = new ArrayList<>();
        for (Model.Entry e : shards.get(0).entries()) {
            paths.add(e.path());
        }
        assertEquals(List.of("tests/test_api.py", "tests/test_auth.py", "tests/test_cli.py",
                "tests/test_db.py", "tests/test_parser.py", "tests/test_utils.py"), paths);
    }

    @Test
    void planLeavesShardsBeyondTheFilesEmpty() {
        List<Model.Entry> entries = worked();

        List<Model.Shard> shards = Planner.plan(entries, 8);

        assertEquals(8, shards.size());
        String[] order = {"tests/test_db.py", "tests/test_parser.py", "tests/test_api.py",
            "tests/test_auth.py", "tests/test_cli.py", "tests/test_utils.py"};
        List<Model.Entry> seen = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Model.Shard shard = shards.get(i);
            assertEquals(i + 1, shard.index());
            long sum = 0;
            for (Model.Entry e : shard.entries()) {
                sum += e.millis();
                seen.add(e);
            }
            assertEquals(sum, shard.millis());
            if (i < 6) {
                assertEquals(1, shard.entries().size());
                assertEquals(order[i], shard.entries().get(0).path());
            } else {
                assertTrue(shard.entries().isEmpty());
                assertEquals(0, shard.millis());
            }
        }
        assertEquals(6, seen.size());
        for (Model.Entry e : entries) {
            assertEquals(1, seen.stream().filter(e::equals).count(), e.path());
        }
    }

    @Test
    void planOfNoFilesGivesEmptyShards() {
        List<Model.Shard> shards = Planner.plan(List.of(), 2);

        assertEquals(List.of(new Model.Shard(1, List.of(), 0), new Model.Shard(2, List.of(), 0)),
                shards);
    }

    @Test
    void neededForTheWorkedValues() {
        List<Model.Entry> entries = worked();

        assertEquals(OptionalInt.of(3), Planner.needed(entries, 45000));
        assertEquals(OptionalInt.of(3), Planner.needed(entries, 42500));
        assertEquals(OptionalInt.of(2), Planner.needed(entries, 51600));
        assertEquals(OptionalInt.of(1), Planner.needed(entries, 100850));
        assertEquals(OptionalInt.empty(), Planner.needed(entries, 42499));
        assertEquals(OptionalInt.empty(), Planner.needed(entries, 30000));
    }

    @Test
    void neededJustBelowATotalTakesOneShardMore() {
        List<Model.Entry> entries = worked();

        assertEquals(OptionalInt.of(3), Planner.needed(entries, 51599));
        assertEquals(OptionalInt.of(2), Planner.needed(entries, 100849));
    }

    @Test
    void neededCanExceedTheLowerBound() {
        List<Model.Entry> entries = List.of(entry("a", 3000), entry("b", 3000), entry("c", 2000),
                entry("d", 2000), entry("e", 2000));

        assertEquals(7000, Planner.plan(entries, 2).get(0).millis());
        assertEquals(OptionalInt.of(3), Planner.needed(entries, 6000));
    }

    @Test
    void neededStopsAtSixtyFourShards() {
        assertEquals(OptionalInt.empty(), Planner.needed(same(65, 1000), 1000));
        assertEquals(OptionalInt.of(64), Planner.needed(same(64, 1000), 1000));
        assertEquals(OptionalInt.of(33), Planner.needed(same(65, 1000), 2000));
    }

    @Test
    void neededOfNoFilesIsEmpty() {
        assertEquals(OptionalInt.empty(), Planner.needed(List.of(), 1000));
    }

    @Test
    void estimateRoundsTheMeanHalfUp() {
        Map<String, Long> timings = new LinkedHashMap<>();
        timings.put("a", 1000L);
        timings.put("b", 1001L);
        timings.put("z", 9000L);

        List<Model.Entry> result = Planner.estimate(List.of("x", "a", "y", "b"), timings);

        assertEquals(List.of(new Model.Entry("x", 1001, true), new Model.Entry("a", 1000, false),
                new Model.Entry("y", 1001, true), new Model.Entry("b", 1001, false)), result);
    }

    @Test
    void estimateRoundsDownBelowTheHalf() {
        Map<String, Long> timings = new LinkedHashMap<>();
        timings.put("a", 1L);
        timings.put("b", 1L);
        timings.put("c", 2L);

        List<Model.Entry> result = Planner.estimate(List.of("a", "b", "c", "n"), timings);

        assertEquals(4, result.size());
        assertEquals(new Model.Entry("a", 1, false), result.get(0));
        assertEquals(new Model.Entry("c", 2, false), result.get(2));
        assertEquals(new Model.Entry("n", 1, true), result.get(3));
    }

    @Test
    void estimateKeepsAKnownTimingOfZero() {
        Map<String, Long> timings = new LinkedHashMap<>();
        timings.put("a", 0L);

        List<Model.Entry> result = Planner.estimate(List.of("a", "n"), timings);

        assertEquals(List.of(new Model.Entry("a", 0, false), new Model.Entry("n", 0, true)), result);
        assertFalse(result.get(0).estimated());
    }

    @Test
    void estimateWithoutAKnownTimingIsOneSecond() {
        Map<String, Long> others = new LinkedHashMap<>();
        others.put("z", 9000L);
        List<Model.Entry> expected =
                List.of(new Model.Entry("p", 1000, true), new Model.Entry("q", 1000, true));

        assertEquals(expected, Planner.estimate(List.of("p", "q"), others));
        assertEquals(expected, Planner.estimate(List.of("p", "q"), new LinkedHashMap<>()));
        assertEquals(List.of(), Planner.estimate(List.of(), others));
    }
}
