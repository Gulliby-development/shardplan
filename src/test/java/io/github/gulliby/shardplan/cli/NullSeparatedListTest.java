package io.github.gulliby.shardplan.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

/** Regression tests for NUL-separated list output and its option handling. */
class NullSeparatedListTest {

    private static final String LF = "\n";
    private static final String PREFIX = "shardplan: ";
    private static final String TIMINGS = lines(
            "# seconds path",
            "42.5 tests/test_db.py",
            "3.2 tests/test_cli.py",
            "18 tests/test_api.py",
            "27.250 tests/test_parser.py",
            "0.8 tests/test_utils.py",
            "9.1 tests/test_auth.py");

    @TempDir
    Path dir;

    private record Result(int code, byte[] stdout, byte[] stderr) {
        String stdoutText() {
            return new String(stdout, StandardCharsets.UTF_8);
        }

        String stderrText() {
            return new String(stderr, StandardCharsets.UTF_8);
        }
    }

    private static String lines(String... values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            text.append(value).append(LF);
        }
        return text.toString();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
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
            return new Result(code, stdout.toByteArray(), stderr.toByteArray());
        }
    }

    private static void assertSuccess(byte[] expected, Result result) {
        assertEquals(0, result.code());
        assertArrayEquals(expected, result.stdout());
        assertArrayEquals(new byte[0], result.stderr());
    }

    private static void assertError(String message, Result result) {
        assertEquals(2, result.code());
        assertArrayEquals(new byte[0], result.stdout());
        assertEquals(PREFIX + message + LF, result.stderrText());
    }

    private static void assertUsage(String message, String... args) {
        assertError(message, run(args));
    }

    private static String display(String arg) {
        return arg.replace('\\', '/');
    }

    @Test
    void readmeShardUsesExactNulBytesAndDefaultOutputRemainsLf() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        assertSuccess(utf8("tests/test_parser.py\0tests/test_utils.py\0"),
                run("list", "--shards=3", "--index", "2", "--null", timings));
        assertSuccess(utf8("tests/test_parser.py\ntests/test_utils.py\n"),
                run("list", "--shards=3", "--index", "2", timings));
    }

    @Test
    void nullFlagWorksBeforeAndAfterArgumentsAndMayRepeat() throws IOException {
        String timings = write("timings.txt", TIMINGS);
        byte[] expected = utf8("tests/test_parser.py\0tests/test_utils.py\0");
        assertSuccess(expected, run("list", "--null", "--shards=3", "--index=2", timings));
        assertSuccess(expected, run("list", "--shards=3", "--index=2", timings, "--null"));
        assertSuccess(expected,
                run("list", "--shards=3", "--null", "--index=2", "--null", timings, "--null"));
    }

    @Test
    void pathsWithSpacesAndNonAsciiCharactersAreUtf8AndNulTerminated() throws IOException {
        String timings = write("paths.txt", lines(
                "5 dir with space/a.py",
                "2 b.py",
                "3 tests/t\u00ebst_\u00fc.py"));
        assertSuccess(utf8("b.py\0dir with space/a.py\0tests/t\u00ebst_\u00fc.py\0"),
                run("list", "--shards", "1", "--index", "1", "--null", timings));
    }

    @Test
    void nullSeparatedOutputWorksWithFilesFilter() throws IOException {
        String timings = write("timings.txt", lines("5 a.py", "2 b.py"));
        String files = write("files.txt", lines("dir with space/a.py", "b.py"));
        assertSuccess(utf8("b.py\0dir with space/a.py\0"),
                run("list", "--shards", "1", "--index", "1", "--files", files, "--null", timings));
    }

    @Test
    void emptyShardProducesNoOutputWithNullOption() throws IOException {
        String timings = write("timings.txt", lines("1 a.py"));
        assertSuccess(new byte[0], run("list", "--shards", "2", "--index", "2", "--null", timings));
    }

    @Test
    void planAndNeedRejectNullAtTheFirstUnsupportedOption() {
        assertUsage("plan does not take --null", "plan", "--null", "--bogus");
        assertUsage("unknown option \"--bogus\"", "plan", "--bogus", "--null");
        assertUsage("need does not take --null", "need", "--null", "--bogus");
        assertUsage("unknown option \"--bogus\"", "need", "--bogus", "--null");
    }

    @Test
    void nullEqualsFormsAndNearMissesRemainUnknownOptions() {
        assertUsage("unknown option \"--null=1\"", "list", "--null=1");
        assertUsage("unknown option \"--null=\"", "list", "--null=");
        assertUsage("unknown option \"--null=x\"", "list", "--null=x");
        assertUsage("unknown option \"--nul\"", "list", "--nul");
    }

    @Test
    void listUsageAndInputErrorsKeepTheirValidationOrder() throws IOException {
        assertUsage("--shards is required", "list");
        assertUsage("--shards is required", "list", "--null", "--index", "1", "timings.txt");
        assertUsage("--index is required", "list", "--null", "--shards", "3", "timings.txt");
        assertUsage("invalid --index \"4\" (1 to 3)",
                "list", "--null", "--shards", "3", "--index", "4", "timings.txt");
        assertUsage("missing TIMINGS", "list", "--null", "--shards", "3", "--index", "2");
        assertUsage("too many arguments", "list", "--shards", "1", "--index", "1", "a", "b");

        String invalidTimings = write("invalid.txt", lines("x a.py"));
        String missingFiles = display(dir.resolve("missing-files.txt").toString());
        assertUsage(display(invalidTimings) + ":1: invalid seconds \"x\"",
                "list", "--shards", "1", "--index", "1", "--files", missingFiles, invalidTimings);
    }
}
