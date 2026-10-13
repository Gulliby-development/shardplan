package io.github.gulliby.shardplan.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end tests of the argument parser and of Cli.run, plus the README docs test. */
class CliTest {

    private static final String LF = "\n";
    private static final String CR = "\r";
    private static final String TAB = "\t";
    private static final char BS = '\\';
    private static final String Q = String.valueOf('"');
    private static final String PREFIX = "shardplan: ";
    private static final String NON_ASCII_PATH = "tests/t\u00ebst_\u00fc.py";

    private static final String USAGE = lines(
            "usage: shardplan plan --shards N [--files LIST] TIMINGS",
            "       shardplan list --shards N --index K [--files LIST] TIMINGS",
            "       shardplan need --target SECONDS [--max-shards N] [--files LIST] TIMINGS");

    private static final String TIMINGS = lines(
            "# seconds path",
            "42.5 tests/test_db.py",
            "3.2 tests/test_cli.py",
            "18 tests/test_api.py",
            "27.250 tests/test_parser.py",
            "0.8 tests/test_utils.py",
            "9.1 tests/test_auth.py");

    private static final String FILES = lines(
            "# every test file today",
            "tests/test_db.py",
            "tests/test_api.py",
            "tests/test_new.py",
            "tests/test_other.py");

    private static final String PLAN_3 = lines(
            "shard 1: 42.500 s, 1 file",
            "  42.500 tests/test_db.py",
            "shard 2: 28.050 s, 2 files",
            "  27.250 tests/test_parser.py",
            "  0.800 tests/test_utils.py",
            "shard 3: 30.300 s, 3 files",
            "  18.000 tests/test_api.py",
            "  9.100 tests/test_auth.py",
            "  3.200 tests/test_cli.py",
            "balance: longest 42.500 s (shard 1), shortest 28.050 s (shard 2)");

    @TempDir
    Path dir;

    private record Result(int code, byte[] out, byte[] err) {
        String stdout() {
            return new String(out, StandardCharsets.UTF_8);
        }

        String stderr() {
            return new String(err, StandardCharsets.UTF_8);
        }
    }

    private static String lines(String... items) {
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            sb.append(item).append(LF);
        }
        return sb.toString();
    }

    private static String q(String text) {
        return Q + text + Q;
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static Result runWith(byte[] stdin, String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, false, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, false, StandardCharsets.UTF_8);
        int code = Cli.run(args, new ByteArrayInputStream(stdin), out, err);
        out.flush();
        err.flush();
        return new Result(code, outBytes.toByteArray(), errBytes.toByteArray());
    }

    private static Result run(String... args) {
        return runWith(new byte[0], args);
    }

    private String writeBytes(String name, byte[] bytes) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, bytes);
        return file.toString();
    }

    private String write(String name, String text) throws IOException {
        return writeBytes(name, utf8(text));
    }

    /** The display name of a path argument: every backslash becomes a slash. */
    private static String display(String arg) {
        return arg.replace(BS, '/');
    }

    private static void assertOk(String stdout, Result result) {
        assertEquals("", result.stderr());
        assertEquals(stdout, result.stdout());
        assertEquals(0, result.code());
    }

    private static void assertError(int code, String message, Result result) {
        assertEquals(PREFIX + message + LF, result.stderr());
        assertEquals(0, result.out().length);
        assertEquals(code, result.code());
    }

    private static void assertUsage(String message, String... args) {
        assertError(2, message, run(args));
    }

    private static void assertNoCr(byte[] bytes) {
        for (byte b : bytes) {
            assertFalse(b == 13);
        }
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        for (int i = 0; i + needle.length <= hay.length; i++) {
            boolean same = true;
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void argsParseReturnsTheRecord() {
        Args plan = Args.parse(new String[] {"plan", "--shards", "3", "t.txt"});
        assertEquals("plan", plan.command());
        assertEquals(3, plan.shards());
        assertEquals(0, plan.index());
        assertEquals(0L, plan.targetMillis());
        assertNull(plan.files());
        assertEquals("t.txt", plan.timings());

        Args list = Args.parse(new String[] {"list", "--shards=3", "--index", "2", "t.txt"});
        assertEquals("list", list.command());
        assertEquals(3, list.shards());
        assertEquals(2, list.index());
        assertEquals("t.txt", list.timings());

        Args need = Args.parse(new String[] {"need", "--target", "42.5", "--files=f.txt", "-"});
        assertEquals("need", need.command());
        assertEquals(42500L, need.targetMillis());
        assertEquals(0, need.shards());
        assertEquals(0, need.index());
        assertEquals("f.txt", need.files());
        assertEquals("-", need.timings());

        Args repeated = Args.parse(new String[] {"plan", "--shards", "2", "--shards", "05", "t.txt"});
        assertEquals("plan", repeated.command());
        assertEquals(5, repeated.shards());

        Args late = Args.parse(new String[] {"plan", "t.txt", "--shards", "4"});
        assertEquals(4, late.shards());
        assertEquals("t.txt", late.timings());

        Args max = Args.parse(new String[] {"list", "--shards", "64", "--index=64", "t.txt"});
        assertEquals(64, max.shards());
        assertEquals(64, max.index());
    }

    @Test
    void argsParseThrowsForUsageErrors() {
        IllegalArgumentException none = assertThrows(IllegalArgumentException.class,
                () -> Args.parse(new String[0]));
        assertEquals("missing command (plan, list or need)", none.getMessage());
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> Args.parse(new String[] {"plan", "--shards", "2"}));
        assertEquals("missing TIMINGS", missing.getMessage());
    }

    @Test
    void usageErrorsFoundWhileReadingArguments() {
        assertUsage("missing command (plan, list or need)");
        assertUsage("unknown command " + q("bogus"), "bogus");
        assertUsage("unknown option " + q("-x"), "plan", "-x");
        assertUsage("unknown option " + q("--foo=1"), "plan", "--foo=1", "e.txt");
        assertUsage("unknown option " + q("--help"), "plan", "--help");
        assertUsage("plan does not take --index", "plan", "--index", "1", "--shards", "2", "e.txt");
        assertUsage("need does not take --shards", "need", "--shards", "3", "timings.txt");
        assertUsage("need does not take --index", "need", "--index=1", "t");
        assertUsage("plan does not take --target", "plan", "--target", "5", "t");
        assertUsage("list does not take --target", "list", "--target", "5", "t");
        assertUsage("need does not take --shards", "need", "--shards");
        assertUsage("--shards needs a value", "plan", "--shards");
        assertUsage("--target needs a value", "need", "--target");
        assertUsage("--files needs a value", "plan", "--shards", "2", "--files");
    }

    @Test
    void usageErrorsFoundWhenArgumentsAreUsedUp() {
        assertUsage("--shards is required", "plan");
        assertUsage("--shards is required", "plan", "e.txt");
        assertUsage("invalid --shards " + q("0"), "plan", "--shards", "0", "e.txt");
        assertUsage("invalid --shards " + q("65"), "plan", "--shards", "65", "e.txt");
        assertUsage("invalid --shards " + q("005"), "plan", "--shards", "005", "e.txt");
        assertUsage("invalid --shards " + q(""), "plan", "--shards=", "e.txt");
        assertUsage("invalid --shards " + q("--files"), "plan", "--shards", "--files", "e.txt");
        assertUsage("missing TIMINGS", "plan", "--shards", "2");
        assertUsage("too many arguments", "plan", "--shards", "2", "a", "b");
        assertUsage("--index is required", "list", "--shards", "3", "timings.txt");
        assertUsage("invalid --index " + q("4") + " (1 to 3)",
                "list", "--shards", "3", "--index", "4", "timings.txt");
        assertUsage("invalid --index " + q("0") + " (1 to 3)",
                "list", "--shards", "3", "--index", "0", "timings.txt");
        assertUsage("invalid --index " + q("x") + " (1 to 3)", "list", "--shards", "3", "--index", "x");
        assertUsage("--shards is required", "list", "--index", "1", "t");
        assertUsage("--target is required", "need", "timings.txt");
        assertUsage("invalid --target " + q("0"), "need", "--target", "0", "timings.txt");
        assertUsage("invalid --target " + q("0.000"), "need", "--target", "0.000", "t");
        assertUsage("invalid --target " + q("1.2345"), "need", "--target", "1.2345", "t");
        assertUsage("missing TIMINGS", "need", "--target", "5");
        assertUsage("invalid --shards " + q("0"), "plan", "--shards", "0");
    }

    @Test
    void helpIsHelpOnlyAsTheSoleArgument() {
        assertOk(USAGE, run("--help"));
        assertUsage("unknown command " + q("--help"), "--help", "plan");
        assertUsage("unknown option " + q("--help"), "plan", "--shards", "2", "--help");
    }

    @Test
    void planPrintsTheWorkedExampleAndLeavesTheInputUnchanged() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        assertOk(PLAN_3, run("plan", "--shards", "3", timings));
        assertArrayEquals(utf8(TIMINGS), Files.readAllBytes(Path.of(timings)));
    }

    @Test
    void planWithMoreShardsThanFilesPrintsEmptyShards() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        Result result = run("plan", "--shards", "8", timings);
        assertOk(lines(
                "shard 1: 42.500 s, 1 file",
                "  42.500 tests/test_db.py",
                "shard 2: 27.250 s, 1 file",
                "  27.250 tests/test_parser.py",
                "shard 3: 18.000 s, 1 file",
                "  18.000 tests/test_api.py",
                "shard 4: 9.100 s, 1 file",
                "  9.100 tests/test_auth.py",
                "shard 5: 3.200 s, 1 file",
                "  3.200 tests/test_cli.py",
                "shard 6: 0.800 s, 1 file",
                "  0.800 tests/test_utils.py",
                "shard 7: 0.000 s, 0 files",
                "shard 8: 0.000 s, 0 files",
                "balance: longest 42.500 s (shard 1), shortest 0.000 s (shard 7)"), result);
        assertFalse(result.stdout().contains("estimated:"));
    }

    @Test
    void planWithFilesEstimatesNewFilesAndCountsIgnoredTimings() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        String files = write("files.txt", FILES);
        assertOk(lines(
                "shard 1: 60.500 s, 2 files",
                "  18.000 tests/test_api.py",
                "  42.500 tests/test_db.py",
                "shard 2: 60.500 s, 2 files",
                "  30.250 tests/test_new.py (estimated)",
                "  30.250 tests/test_other.py (estimated)",
                "balance: longest 60.500 s (shard 1), shortest 60.500 s (shard 1)",
                "estimated: 2, ignored: 4"), run("plan", "--files", files, "--shards", "2", timings));
    }

    @Test
    void planWithFilesThatListEveryTimingPrintsZeroCounts() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        String all = write("all.txt", lines(
                "tests/test_utils.py",
                "tests/test_auth.py",
                "tests/test_db.py",
                "tests/test_cli.py",
                "tests/test_api.py",
                "tests/test_parser.py"));
        assertOk(PLAN_3 + "estimated: 0, ignored: 0" + LF,
                run("plan", "--shards", "3", "--files", all, timings));
    }

    @Test
    void listPrintsThePathsOfOneShard() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        assertOk(lines("tests/test_parser.py", "tests/test_utils.py"),
                run("list", "--shards=3", "--index", "2", timings));
        assertOk(lines("tests/test_db.py"), run("list", "--shards=3", "--index=1", timings));
        Result empty = run("list", "--shards", "8", "--index", "7", timings);
        assertOk("", empty);
        assertEquals(0, empty.out().length);
    }

    @Test
    void listWithFilesPrintsPathsOnly() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        String files = write("files.txt", FILES);
        assertOk(lines("tests/test_new.py", "tests/test_other.py"),
                run("list", "--shards", "2", "--index", "2", "--files", files, timings));
    }

    @Test
    void needPrintsTheSmallestShardCount() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        String files = write("files.txt", FILES);
        String three = "shards needed: 3 (longest 42.500 s)" + LF;
        assertOk(three, run("need", "--target", "45", timings));
        assertOk(three, run("need", "--target", "42.5", timings));
        assertOk("shards needed: 2 (longest 60.500 s)" + LF,
                run("need", "--target", "60.5", "--files=" + files, timings));
        assertOk("shards needed: 2 (longest 51.600 s)" + LF, run("need", "--target", "51.6", timings));
        assertOk("shards needed: 1 (longest 100.850 s)" + LF, run("need", "--target=100.85", timings));
    }

    @Test
    void needExitsOneWhenTheLongestFileIsLongerThanTheTarget() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        assertError(1, "tests/test_db.py takes 42.500 s, longer than the target 30.000 s",
                run("need", "--target", "30", timings));
        assertError(1, "tests/test_db.py takes 42.500 s, longer than the target 42.400 s",
                run("need", "--target", "42.4", timings));
    }

    @Test
    void needExitsOneAtTheShardLimitAndBreaksTiesByPath() throws IOException {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            many.append("1 f").append(i).append(LF);
        }
        String big = write("many.txt", many.toString());
        assertError(1, "the target 1.000 s needs more than 64 shards", run("need", "--target", "1", big));

        String tie = write("tie.txt", lines("2 b", "2 a"));
        assertError(1, "a takes 2.000 s, longer than the target 1.000 s", run("need", "--target", "1", tie));
        assertOk("shards needed: 2 (longest 2.000 s)" + LF, run("need", "--target", "2", tie));
    }

    @Test
    void standardInputWithCrlfGivesTheSameOutputAsAFile() throws IOException {
        byte[] bytes = utf8("1.5 a.py" + CR + LF + CR + LF + "  # c" + CR + LF
                + "2" + TAB + "dir with space/b.py  " + CR + LF + "0 z");
        String expected = lines(
                "shard 1: 2.000 s, 1 file",
                "  2.000 dir with space/b.py",
                "shard 2: 1.500 s, 2 files",
                "  1.500 a.py",
                "  0.000 z",
                "balance: longest 2.000 s (shard 1), shortest 1.500 s (shard 2)");
        Result fromStdin = runWith(bytes, "plan", "--shards", "2", "-");
        assertOk(expected, fromStdin);
        assertNoCr(fromStdin.out());

        Result fromFile = run("plan", "--shards", "2", writeBytes("crlf.txt", bytes));
        assertOk(expected, fromFile);
        assertArrayEquals(fromStdin.out(), fromFile.out());
    }

    @Test
    void standardInputErrorsUseTheStdinDisplayName() {
        assertError(2, "<stdin>:1: missing path", runWith(utf8("1.5" + LF), "plan", "--shards", "2", "-"));
        assertError(2, "<stdin>: input is not valid UTF-8",
                runWith(new byte[] {(byte) 0xFF, (byte) 0xFE}, "plan", "--shards", "2", "-"));
        assertError(2, "<stdin>: no files", runWith(new byte[0], "plan", "--shards", "2", "-"));
    }

    @Test
    void unreadableFilesAndInvalidBytesAreInputErrors() throws IOException {
        String missing = dir.resolve("nofile.txt").toString();
        assertError(2, display(missing) + ": cannot read the file", run("plan", "--shards", "2", missing));

        String folder = dir.toString();
        assertError(2, display(folder) + ": cannot read the file", run("plan", "--shards", "2", folder));

        String bad = writeBytes("bad.txt", new byte[] {'1', ' ', (byte) 0xC3, (byte) 0x28, 10});
        assertError(2, display(bad) + ": input is not valid UTF-8", run("plan", "--shards", "2", bad));

        String relative = "no" + BS + "such" + BS + "file.txt";
        assertError(2, "no/such/file.txt: cannot read the file", run("plan", "--shards", "2", relative));
    }

    @Test
    void listFileErrorsComeAfterTheTimingsFile() throws IOException {
        String timings = write("timings.txt", TIMINGS);

        String missing = dir.resolve("nolist.txt").toString();
        assertError(2, display(missing) + ": cannot read the file",
                run("plan", "--shards", "2", "--files", missing, timings));

        String bad = writeBytes("badlist.txt", new byte[] {(byte) 0xFF, (byte) 0xFE});
        assertError(2, display(bad) + ": input is not valid UTF-8",
                run("plan", "--shards", "2", "--files", bad, timings));

        String comment = write("comment.txt", lines("# nothing"));
        assertError(2, display(comment) + ": no files",
                run("plan", "--shards", "2", "--files", comment, timings));

        String twice = write("twice.txt", lines("a", "a"));
        assertError(2, display(twice) + ":2: duplicate path " + q("a"),
                run("plan", "--shards", "2", "--files", twice, timings));

        assertError(2, "-: cannot read the file",
                runWith(utf8(lines("tests/test_db.py")), "plan", "--shards", "2", "--files", "-", timings));

        String wrong = write("wrong.txt", lines("x a"));
        assertError(2, display(wrong) + ":1: invalid seconds " + q("x"),
                run("plan", "--shards", "2", "--files", missing, wrong));
    }

    @Test
    void timingsWithoutEntriesAreAcceptedOnlyWithFiles() throws IOException {
        String timings = write("empty.txt", lines("# no timing yet"));
        String list = write("list.txt", lines("b", "a"));
        assertOk(lines(
                "shard 1: 1.000 s, 1 file",
                "  1.000 a (estimated)",
                "shard 2: 1.000 s, 1 file",
                "  1.000 b (estimated)",
                "balance: longest 1.000 s (shard 1), shortest 1.000 s (shard 1)",
                "estimated: 2, ignored: 0"), run("plan", "--shards", "2", "--files", list, timings));
        assertError(2, display(timings) + ": no files", run("plan", "--shards", "2", timings));
    }

    @Test
    void everyCommandReportsAnInputErrorWithAnEmptyStdout() throws IOException {
        String file = write("e.txt", lines("1 a", "1 a"));
        String message = display(file) + ":2: duplicate path " + q("a");
        assertError(2, message, run("plan", "--shards", "2", file));
        assertError(2, message, run("list", "--shards", "2", "--index", "1", file));
        assertError(2, message, run("need", "--target", "5", file));
    }

    @Test
    void outputIsRepeatableUtf8WithoutBomOrCarriageReturn() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        Result first = run("plan", "--shards", "3", timings);
        Result second = run("plan", "--shards", "3", timings);
        assertEquals(0, first.code());
        assertArrayEquals(utf8(PLAN_3), first.out());
        assertArrayEquals(first.out(), second.out());
        assertNoCr(first.out());

        String accents = write("accents.txt", lines("1 " + NON_ASCII_PATH));
        Result third = run("plan", "--shards", "1", accents);
        Result fourth = run("plan", "--shards", "1", accents);
        assertEquals(0, third.code());
        assertEquals(0, third.err().length);
        byte[] expected = utf8(lines(
                "shard 1: 1.000 s, 1 file",
                "  1.000 " + NON_ASCII_PATH,
                "balance: longest 1.000 s (shard 1), shortest 1.000 s (shard 1)"));
        assertArrayEquals(expected, third.out());
        assertArrayEquals(third.out(), fourth.out());
        assertNoCr(third.out());
        assertFalse(third.out()[0] == (byte) 0xEF && third.out()[1] == (byte) 0xBB
                && third.out()[2] == (byte) 0xBF);
        byte[] pathBytes = {'t', (byte) 0xC3, (byte) 0xAB, 's', 't', '_', (byte) 0xC3, (byte) 0xBC, '.', 'p', 'y'};
        assertTrue(indexOf(third.out(), pathBytes) > 0);
    }

    @Test
    void readmeHoldsTheUsageTheExamplesAndTheHeadings() throws IOException {
        String readme = new String(Files.readAllBytes(Path.of("README.md")), StandardCharsets.UTF_8)
                .replace(CR + LF, LF);
        assertTrue(readme.contains(USAGE));
        assertTrue(readme.contains(TIMINGS));
        String[] outputLines = {
            "shard 1: 42.500 s, 1 file",
            "  42.500 tests/test_db.py",
            "shard 2: 28.050 s, 2 files",
            "shard 3: 30.300 s, 3 files",
            "balance: longest 42.500 s (shard 1), shortest 28.050 s (shard 2)",
        };
        for (String line : outputLines) {
            assertTrue(readme.contains(LF + line + LF), line);
        }
        String[] headings = {
            "## Install",
            "## Usage",
            "## Timings file format",
            "## How shards are planned",
            "## Exit codes",
            "## Development",
        };
        for (String heading : headings) {
            assertTrue(readme.contains(LF + heading + LF), heading);
        }
    }
}
