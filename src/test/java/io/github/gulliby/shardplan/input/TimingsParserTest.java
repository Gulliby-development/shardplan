package io.github.gulliby.shardplan.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimingsParserTest {

    private static final int LIMIT = 2000;

    private static String timingsError(String display, String text) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TimingsParser.parseTimings(display, text));
        return e.getMessage();
    }

    private static String pathsError(String display, String text) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TimingsParser.parsePaths(display, text));
        return e.getMessage();
    }

    private static List<String> keys(LinkedHashMap<String, Long> map) {
        return new ArrayList<>(map.keySet());
    }

    private static List<Long> values(LinkedHashMap<String, Long> map) {
        return new ArrayList<>(map.values());
    }

    /** Builds {@code count} timings lines {@code 1 f0}, {@code 1 f1}, ... each ended by LF. */
    private static StringBuilder timingsLines(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("1 f").append(i).append('\n');
        }
        return sb;
    }

    /** Builds {@code count} list lines {@code f0}, {@code f1}, ... each ended by LF. */
    private static StringBuilder pathLines(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append('f').append(i).append('\n');
        }
        return sb;
    }

    // ---- parseTimings: accepted texts ----

    @Test
    void timingsWithBomCrlfBlankCommentTabAndNoFinalLineEnd() {
        String text = "\uFEFF1.5 a.py\r\n\r\n  # c\r\n2\tdir with space/b.py  \r\n0 z";
        LinkedHashMap<String, Long> map = TimingsParser.parseTimings("t.txt", text);
        assertEquals(List.of("a.py", "dir with space/b.py", "z"), keys(map));
        assertEquals(List.of(1500L, 2000L, 0L), values(map));
    }

    @Test
    void timingsWithLfAndFinalLineEndGiveTheSameMap() {
        String crlf = "\uFEFF1.5 a.py\r\n\r\n  # c\r\n2\tdir with space/b.py  \r\n0 z";
        String lf = "1.5 a.py\n\n  # c\n2\tdir with space/b.py  \n0 z\n";
        LinkedHashMap<String, Long> expected = TimingsParser.parseTimings("t.txt", crlf);
        LinkedHashMap<String, Long> actual = TimingsParser.parseTimings("t.txt", lf);
        assertEquals(keys(expected), keys(actual));
        assertEquals(values(expected), values(actual));
        assertEquals(3, actual.size());
    }

    @Test
    void timingsKeepFileOrderAndNotSortedOrder() {
        LinkedHashMap<String, Long> map = TimingsParser.parseTimings("t.txt", "1 c\n2 a\n3 b\n");
        assertEquals(List.of("c", "a", "b"), keys(map));
        assertEquals(List.of(1000L, 2000L, 3000L), values(map));
    }

    @Test
    void timingsPathMayHoldSpacesTabsAndHashAndIsTakenAsItIs() {
        String text = "1 \t  a  b\tc \n0.001 #x\n99999.999 dir\\win.py\n";
        LinkedHashMap<String, Long> map = TimingsParser.parseTimings("t.txt", text);
        assertEquals(List.of("a  b\tc", "#x", "dir\\win.py"), keys(map));
        assertEquals(List.of(1000L, 1L, 99999999L), values(map));
    }

    @Test
    void timingsPathsThatDifferOnlyInCaseAreNotDuplicates() {
        LinkedHashMap<String, Long> map = TimingsParser.parseTimings("t.txt", "1 a\n2 A\n");
        assertEquals(List.of("a", "A"), keys(map));
        assertEquals(List.of(1000L, 2000L), values(map));
    }

    @Test
    void timingsCommentLineThatLooksLikeAnEntryIsSkipped() {
        LinkedHashMap<String, Long> map = TimingsParser.parseTimings("t.txt", "#1 a\n\t# 2 b\n3 c\n");
        assertEquals(List.of("c"), keys(map));
        assertEquals(List.of(3000L), values(map));
    }

    @Test
    void timingsTrailingCrWithoutLfIsNotRemoved() {
        LinkedHashMap<String, Long> map = TimingsParser.parseTimings("t.txt", "1 a\r");
        assertEquals(List.of("a\r"), keys(map));
    }

    @Test
    void timingsLoneCrIsNotALineBreakAndOnlyOneCrIsRemoved() {
        LinkedHashMap<String, Long> map = TimingsParser.parseTimings("t.txt", "1 a\rb\n2 c\r\r\n");
        assertEquals(List.of("a\rb", "c\r"), keys(map));
        assertEquals(List.of(1000L, 2000L), values(map));
    }

    @Test
    void timingsEmptyTextGivesAnEmptyMap() {
        assertTrue(TimingsParser.parseTimings("t.txt", "").isEmpty());
        assertTrue(TimingsParser.parseTimings("t.txt", "\uFEFF").isEmpty());
        assertTrue(TimingsParser.parseTimings("t.txt", "\n").isEmpty());
    }

    @Test
    void timingsOnlyCommentsAndBlankLinesGiveAnEmptyMap() {
        String text = "# seconds path\r\n\r\n \t \n  # another\n\n";
        assertEquals(0, TimingsParser.parseTimings("t.txt", text).size());
    }

    // ---- parseTimings: messages ----

    @Test
    void timingsMissingPath() {
        assertEquals("e.txt:1: missing path", timingsError("e.txt", "1.5"));
        assertEquals("e.txt:1: missing path", timingsError("e.txt", "1.5\n"));
        assertEquals("e.txt:1: missing path", timingsError("e.txt", " \t1.5 \t \r\n"));
    }

    @Test
    void timingsInvalidSeconds() {
        assertEquals("e.txt:1: invalid seconds \"x\"", timingsError("e.txt", "x tests/a"));
        assertEquals("e.txt:1: invalid seconds \"1.2345\"", timingsError("e.txt", "1.2345 a"));
        assertEquals("e.txt:1: invalid seconds \"tests/a.py\"", timingsError("e.txt", "tests/a.py"));
        assertEquals("e.txt:1: invalid seconds \"-1\"", timingsError("e.txt", "-1\ta"));
    }

    @Test
    void timingsInvalidSecondsIsReportedBeforeMissingPath() {
        assertEquals("e.txt:1: invalid seconds \"x\"", timingsError("e.txt", "x\n"));
    }

    @Test
    void timingsDuplicatePath() {
        assertEquals("e.txt:2: duplicate path \"a\"", timingsError("e.txt", "1 a\n1 a\n"));
        assertEquals("e.txt:4: duplicate path \"dir with space/b.py\"",
                timingsError("e.txt", "1 dir with space/b.py\r\n2 c\r\n\r\n3\tdir with space/b.py \r\n"));
    }

    @Test
    void timingsSkippedLinesCountInTheNumbering() {
        assertEquals("e.txt:3: missing path", timingsError("e.txt", "# comment\n\n1.5\n"));
        assertEquals("e.txt:3: missing path", timingsError("e.txt", "# comment\r\n\r\n1.5"));
        assertEquals("e.txt:2: missing path", timingsError("e.txt", "\n1.5\n"));
    }

    @Test
    void timingsBomDoesNotShiftTheNumbering() {
        assertEquals("e.txt:1: missing path", timingsError("e.txt", "\uFEFF1.5\n"));
        assertEquals("e.txt:3: missing path", timingsError("e.txt", "\uFEFF# comment\n\n1.5\n"));
        assertEquals("e.txt:2: duplicate path \"a\"", timingsError("e.txt", "\uFEFF1 a\r\n2 a"));
    }

    @Test
    void timingsOnlyOneLeadingBomIsSkipped() {
        assertEquals("e.txt:1: invalid seconds \"\uFEFF1\"", timingsError("e.txt", "\uFEFF\uFEFF1 a\n"));
        assertEquals("e.txt:2: invalid seconds \"\uFEFF2\"", timingsError("e.txt", "1 a\n\uFEFF2 b\n"));
    }

    @Test
    void timingsMessageUsesTheDisplayNameAsGiven() {
        assertEquals("<stdin>:1: missing path", timingsError("<stdin>", "1.5"));
        assertEquals("some dir/t.txt:2: invalid seconds \"y\"", timingsError("some dir/t.txt", "1 a\ny b\n"));
    }

    // ---- the limit of 2000 files ----

    @Test
    void timingsAcceptExactly2000Entries() {
        LinkedHashMap<String, Long> map = TimingsParser.parseTimings("big.txt", timingsLines(LIMIT).toString());
        assertEquals(2000, map.size());
        assertEquals("f0", keys(map).get(0));
        assertEquals("f1999", keys(map).get(1999));
    }

    @Test
    void timingsRejectThe2001stEntry() {
        String text = timingsLines(LIMIT + 1).toString();
        assertEquals("big.txt:2001: more than 2000 files", timingsError("big.txt", text));
    }

    @Test
    void timingsLimitComesBeforeTheDuplicateCheck() {
        String text = timingsLines(LIMIT).append("1 f0\n").toString();
        assertEquals("big.txt:2001: more than 2000 files", timingsError("big.txt", text));
    }

    @Test
    void timingsLimitCountsEntriesAndNotLines() {
        String text = "# header\n\n" + timingsLines(LIMIT);
        assertEquals(2000, TimingsParser.parseTimings("big.txt", text).size());
        assertEquals("big.txt:2003: more than 2000 files", timingsError("big.txt", text + "1 last\n"));
    }

    @Test
    void pathsAcceptExactly2000Paths() {
        List<String> paths = TimingsParser.parsePaths("big.txt", pathLines(LIMIT).toString());
        assertEquals(2000, paths.size());
        assertEquals("f0", paths.get(0));
        assertEquals("f1999", paths.get(1999));
    }

    @Test
    void pathsRejectThe2001stPath() {
        String text = pathLines(LIMIT + 1).toString();
        assertEquals("big.txt:2001: more than 2000 files", pathsError("big.txt", text));
    }

    @Test
    void pathsLimitComesBeforeTheDuplicateCheck() {
        String text = pathLines(LIMIT).append("f0\n").toString();
        assertEquals("big.txt:2001: more than 2000 files", pathsError("big.txt", text));
    }

    // ---- parsePaths ----

    @Test
    void pathsWithBomCrlfCommentBlankLineSpacesAndNoFinalLineEnd() {
        String text = "\uFEFF# every test file today\r\n\r\n \t dir with space/b.py\t \r\nz.py\r\na.py";
        List<String> paths = TimingsParser.parsePaths("files.txt", text);
        assertEquals(List.of("dir with space/b.py", "z.py", "a.py"), paths);
    }

    @Test
    void pathsWithLfAndFinalLineEndGiveTheSameList() {
        String text = "# every test file today\n\n \t dir with space/b.py\t \nz.py\na.py\n";
        assertEquals(List.of("dir with space/b.py", "z.py", "a.py"), TimingsParser.parsePaths("files.txt", text));
    }

    @Test
    void pathsTakeTheWholeTrimmedLineAsItIs() {
        List<String> paths = TimingsParser.parsePaths("files.txt", "1.5 a.py\nA\na\ndir\\w.py\n");
        assertEquals(List.of("1.5 a.py", "A", "a", "dir\\w.py"), paths);
    }

    @Test
    void pathsDuplicateIsReportedWithItsLine() {
        assertEquals("files.txt:3: duplicate path \"a\"", pathsError("files.txt", "a\nb\na\n"));
        assertEquals("files.txt:2: duplicate path \"a\"", pathsError("files.txt", "\uFEFFa\r\n\ta "));
        assertEquals("files.txt:4: duplicate path \"x y\"", pathsError("files.txt", "# c\nx y\n\n x y\n"));
    }

    @Test
    void pathsWithoutAnyPathAreAnError() {
        assertEquals("files.txt: no files", pathsError("files.txt", ""));
        assertEquals("files.txt: no files", pathsError("files.txt", "\uFEFF"));
        assertEquals("files.txt: no files", pathsError("files.txt", "# only a comment\r\n\r\n \t \n  # c\n"));
    }

    @Test
    void pathsWithOnePathAreAccepted() {
        assertEquals(List.of("a"), TimingsParser.parsePaths("files.txt", "a"));
    }
}
