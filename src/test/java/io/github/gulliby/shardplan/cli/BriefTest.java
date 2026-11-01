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

/** Tests for plan --brief: the plan without its file lines. */
class BriefTest {

    private static final String LF = "\n";
    private static final String TIMINGS = lines(
            "# seconds path",
            "42.5 tests/test_db.py",
            "3.2 tests/test_cli.py",
            "18 tests/test_api.py",
            "27.250 tests/test_parser.py",
            "0.8 tests/test_utils.py",
            "9.1 tests/test_auth.py");
    private static final String BRIEF_PLAN = lines(
            "shard 1: 42.500 s, 1 file",
            "shard 2: 28.050 s, 2 files",
            "shard 3: 30.300 s, 3 files",
            "balance: longest 42.500 s (shard 1), shortest 28.050 s (shard 2)");

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

    @Test
    void readmeExampleLeavesOutTheFileLines() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        assertEquals(new Result(0, BRIEF_PLAN, ""), run("plan", "--shards", "3", "--brief", timings));
    }

    @Test
    void flagStandsAnywhereMayRepeatAndTakesNoValue() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        assertEquals(new Result(0, BRIEF_PLAN, ""), run("plan", "--brief", "--shards=3", timings));
        assertEquals(new Result(0, BRIEF_PLAN, ""), run("plan", "--shards", "3", timings, "--brief"));
        assertEquals(new Result(0, BRIEF_PLAN, ""), run("plan", "--brief", "--shards", "3", "--brief", timings, "--brief"));
        assertEquals(new Result(2, "", "shardplan: unknown option \"--brief=1\"" + LF),
                run("plan", "--shards", "3", "--brief=1", timings));
    }

    @Test
    void emptyShardsAndSingleFilePrintTheirHeaderOnly() throws IOException {
        String timings = write("one.txt", "5 only.py\n");
        assertEquals(new Result(0, lines(
                "shard 1: 5.000 s, 1 file",
                "shard 2: 0.000 s, 0 files",
                "balance: longest 5.000 s (shard 1), shortest 0.000 s (shard 2)"), ""),
                run("plan", "--shards", "2", "--brief", timings));
    }

    @Test
    void estimatedLineAndEstimatedMarksFollowTheFilesList() throws IOException {
        String timings = write("timings.txt", "42.5 tests/test_db.py\n18 tests/test_api.py\n9 tests/gone.py\n");
        String files = write("files.txt", "tests/test_db.py\ntests/test_api.py\ntests/test_new.py\ntests/test_other.py\n");
        assertEquals(new Result(0, lines(
                "shard 1: 60.500 s, 2 files",
                "shard 2: 60.500 s, 2 files",
                "balance: longest 60.500 s (shard 1), shortest 60.500 s (shard 1)",
                "estimated: 2, ignored: 1"), ""),
                run("plan", "--files", files, "--shards", "2", "--brief", timings));
    }

    @Test
    void listAndNeedDoNotTakeTheFlag() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        assertEquals(new Result(2, "", "shardplan: list does not take --brief" + LF),
                run("list", "--shards", "3", "--index", "1", "--brief", timings));
        assertEquals(new Result(2, "", "shardplan: need does not take --brief" + LF),
                run("need", "--target", "45", "--brief", timings));
    }

    @Test
    void planWithoutTheFlagStillListsTheFiles() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        Result plain = run("plan", "--shards", "3", timings);
        assertEquals(0, plain.code());
        assertEquals(lines(
                "shard 1: 42.500 s, 1 file",
                "  42.500 tests/test_db.py",
                "shard 2: 28.050 s, 2 files",
                "  27.250 tests/test_parser.py",
                "  0.800 tests/test_utils.py",
                "shard 3: 30.300 s, 3 files",
                "  18.000 tests/test_api.py",
                "  9.100 tests/test_auth.py",
                "  3.200 tests/test_cli.py",
                "balance: longest 42.500 s (shard 1), shortest 28.050 s (shard 2)"), plain.stdout());
    }
}
