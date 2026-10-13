package io.github.gulliby.shardplan.input;

import io.github.gulliby.shardplan.plan.Seconds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

/** Parses the timings file and the list file from their decoded text. */
public final class TimingsParser {

    private static final int MAX_FILES = 2000;
    private static final char BOM = '\uFEFF';

    private TimingsParser() {
    }

    /**
     * Parses lines of the form {@code SECONDS PATH} into a map of path to
     * milliseconds in file order; a text without entries gives an empty map.
     */
    public static LinkedHashMap<String, Long> parseTimings(String display, String text) {
        LinkedHashMap<String, Long> timings = new LinkedHashMap<>();
        List<String> lines = splitLines(text);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isSkipped(line)) {
                continue;
            }
            int number = i + 1;
            int split = firstBlank(line);
            String secondsText = line.substring(0, split);
            OptionalLong millis = Seconds.parse(secondsText);
            if (millis.isEmpty()) {
                throw problem(display, number, "invalid seconds \"" + secondsText + "\"");
            }
            String path = trimBlanks(line.substring(split));
            if (path.isEmpty()) {
                throw problem(display, number, "missing path");
            }
            checkEntry(display, number, timings.size(), timings.containsKey(path), path);
            timings.put(path, millis.getAsLong());
        }
        return timings;
    }

    /** Parses a list file with one path per line; a list without paths is an error. */
    public static List<String> parsePaths(String display, String text) {
        List<String> paths = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> lines = splitLines(text);
        for (int i = 0; i < lines.size(); i++) {
            String path = lines.get(i);
            if (isSkipped(path)) {
                continue;
            }
            checkEntry(display, i + 1, paths.size(), seen.contains(path), path);
            seen.add(path);
            paths.add(path);
        }
        if (paths.isEmpty()) {
            throw new IllegalArgumentException(display + ": no files");
        }
        return paths;
    }

    /** The limit check comes first, then the duplicate check. */
    private static void checkEntry(String display, int number, int count, boolean duplicate, String path) {
        if (count >= MAX_FILES) {
            throw problem(display, number, "more than " + MAX_FILES + " files");
        }
        if (duplicate) {
            throw problem(display, number, "duplicate path \"" + path + "\"");
        }
    }

    private static IllegalArgumentException problem(String display, int number, String message) {
        return new IllegalArgumentException(display + ":" + number + ": " + message);
    }

    /**
     * Splits the text at LF after one leading BOM, removes one CR directly
     * before each LF and trims every line of spaces and tabs. The index of a
     * line plus 1 is its line number.
     */
    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        int length = text.length();
        int start = length > 0 && text.charAt(0) == BOM ? 1 : 0;
        while (start < length) {
            int lf = text.indexOf('\n', start);
            int end = lf < 0 ? length : lf;
            int next = lf < 0 ? length : lf + 1;
            if (lf > start && text.charAt(lf - 1) == '\r') {
                end = lf - 1;
            }
            lines.add(trimBlanks(text.substring(start, end)));
            start = next;
        }
        return lines;
    }

    private static boolean isSkipped(String line) {
        return line.isEmpty() || line.charAt(0) == '#';
    }

    /** Index of the first space or tab, or the length when there is none. */
    private static int firstBlank(String line) {
        int pos = 0;
        while (pos < line.length() && !isBlank(line.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    /** Removes spaces and tabs, and nothing else, at both ends. */
    private static String trimBlanks(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isBlank(value.charAt(start))) {
            start++;
        }
        while (end > start && isBlank(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t';
    }
}
