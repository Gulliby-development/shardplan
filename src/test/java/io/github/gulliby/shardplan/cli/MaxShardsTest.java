package io.github.gulliby.shardplan.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression tests for the need-only shard limit. */
class MaxShardsTest {

    private static final String LF = "\n";
    private static final String TIMINGS = lines(
            "# seconds path",
            "42.5 tests/test_db.py",
            "3.2 tests/test_cli.py",
            "18 tests/test_api.py",
            "27.250 tests/test_parser.py",
            "0.8 tests/test_utils.py",
            "9.1 tests/test_auth.py");
    private static final String FILES = lines(
            "tests/test_db.py",
            "tests/test_api.py",
            "tests/test_new.py",
            "tests/test_other.py");

    @TempDir
    Path dir;

    private record Result(int code, String stdout, String stderr) {
    }

    private static String lines(String... values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            text.append(value).append(LF);
        }
        return text.toString();
    }

    private String write(String name, String text) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file.toString();
    }

    private static Result run(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(stdout, false, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(stderr, false, StandardCharsets.UTF_8)) {
            int code = Cli.run(args, new ByteArrayInputStream(new byte[0]), out, err);
            out.flush();
            err.flush();
            return new Result(code, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
        }
    }

    private static void assertSuccess(String expected, Result result) {
        assertEquals(0, result.code());
        assertEquals(expected, result.stdout());
        assertEquals("", result.stderr());
    }

    private static void assertError(int code, String message, Result result) {
        assertEquals(code, result.code());
        assertEquals("", result.stdout());
        assertEquals("shardplan: " + message + LF, result.stderr());
    }

    private static void assertUsage(String message, String... args) {
        assertError(2, message, run(args));
    }

    @Test
    void limitAllowsTheBoundaryAndRejectsCountsAboveIt() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        assertSuccess("shards needed: 3 (longest 42.500 s)" + LF,
                run("need", "--target=45", "--max-shards=3", timings));
        assertSuccess("shards needed: 3 (longest 42.500 s)" + LF,
                run("need", "--target", "45", timings, "--max-shards", "3"));
        assertSuccess("shards needed: 1 (longest 100.850 s)" + LF,
                run("need", "--target", "100.85", "--max-shards", "1", timings));
        assertError(1, "the target 45.000 s needs more than 2 shards",
                run("need", "--target", "45", "--max-shards", "2", timings));
        assertError(1, "the target 45.000 s needs more than 1 shard",
                run("need", "--target=45", "--max-shards=1", timings));
    }

    @Test
    void defaultLimitAndExplicitLimitReportTheirExactBounds() throws IOException {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            many.append("1 f").append(i).append(LF);
        }
        String timings = write("many.txt", many.toString());
        assertError(1, "the target 1.000 s needs more than 64 shards",
                run("need", "--target", "1", timings));
        assertError(1, "the target 2.000 s needs more than 10 shards",
                run("need", "--max-shards", "10", "--target", "2", timings));
    }

    @Test
    void longestFileErrorIsCheckedBeforeTheShardLimit() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        assertError(1, "tests/test_db.py takes 42.500 s, longer than the target 30.000 s",
                run("need", "--target", "30", "--max-shards", "1", timings));
    }

    @Test
    void lastRepeatedLimitWinsAndFilesOptionStillWorks() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        String files = write("files.txt", FILES);
        assertSuccess("shards needed: 2 (longest 60.500 s)" + LF,
                run("need", "--target", "60.5", "--max-shards", "1", "--files", files,
                        "--max-shards", "2", timings));
        assertError(1, "the target 60.500 s needs more than 1 shard",
                run("need", "--target", "60.5", "--max-shards=2", "--files=" + files,
                        timings, "--max-shards=1"));
    }

    @Test
    void argumentsExposeTheDefaultAndConfiguredLimit() {
        Args defaultLimit = Args.parse(new String[] {"need", "--target", "5", "t"});
        assertEquals(64, defaultLimit.maxShards());

        Args configuredLimit = Args.parse(new String[] {"need", "--target", "5", "--max-shards", "7", "t"});
        assertEquals(7, configuredLimit.maxShards());

        Args maximumLimit = Args.parse(new String[] {"need", "--target", "5", "--max-shards=64", "t"});
        assertEquals(64, maximumLimit.maxShards());
    }

    @Test
    void usageErrorsFollowTheSpecifiedParsingAndValidationOrder() {
        assertUsage("plan does not take --max-shards", "plan", "--max-shards", "2", "t");
        assertUsage("list does not take --max-shards", "list", "--max-shards=2", "t");
        assertUsage("--max-shards needs a value", "need", "--target", "5", "--max-shards");

        assertUsage("--target is required", "need", "--max-shards", "0", "t");
        assertUsage("--target is required", "need", "--max-shards", "2", "t");
        assertUsage("invalid --target \"x\"", "need", "--target", "x", "--max-shards", "0", "t");
        assertUsage("invalid --max-shards \"0\"", "need", "--target", "5", "--max-shards", "0", "t");
        assertUsage("invalid --max-shards \"65\"", "need", "--target", "5", "--max-shards", "65", "t");
        assertUsage("invalid --max-shards \"007\"", "need", "--target", "5", "--max-shards", "007", "t");
        assertUsage("invalid --max-shards \"\"", "need", "--target", "5", "--max-shards=", "t");
        assertUsage("invalid --max-shards \"x\"", "need", "--target", "5", "--max-shards", "x", "t");
        assertUsage("invalid --max-shards \"0\"", "need", "--target", "5", "--max-shards", "0");
        assertUsage("missing TIMINGS", "need", "--target", "5", "--max-shards", "1");
        assertUsage("too many arguments", "need", "--target", "5", "--max-shards", "1", "a", "b");
    }
}
