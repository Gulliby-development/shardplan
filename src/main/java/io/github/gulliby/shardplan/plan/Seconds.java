package io.github.gulliby.shardplan.plan;

import java.util.OptionalLong;

/** Reads and writes a time in seconds as whole milliseconds. */
public final class Seconds {

    private static final int MAX_WHOLE_DIGITS = 5;
    private static final int FRACTION_DIGITS = 3;
    private static final int MAX_MILLIS_DIGITS = 8;

    private Seconds() {
    }

    /**
     * Parses {@code N} or {@code N.F} (N 1 to 5 ASCII digits, F 1 to 3 ASCII
     * digits), each with an optional {@code s} after it, or {@code Nms} (N 1 to
     * 8 ASCII digits, whole milliseconds), into milliseconds; empty when the
     * text is not valid.
     */
    public static OptionalLong parse(String text) {
        if (text == null) {
            return OptionalLong.empty();
        }
        if (text.endsWith("ms")) {
            return parseMillis(text.substring(0, text.length() - 2));
        }
        if (text.endsWith("s")) {
            return parseSeconds(text.substring(0, text.length() - 1));
        }
        return parseSeconds(text);
    }

    private static OptionalLong parseMillis(String text) {
        int length = text.length();
        if (length < 1 || length > MAX_MILLIS_DIGITS) {
            return OptionalLong.empty();
        }
        long millis = 0;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (!isDigit(c)) {
                return OptionalLong.empty();
            }
            millis = millis * 10 + (c - '0');
        }
        return OptionalLong.of(millis);
    }

    private static OptionalLong parseSeconds(String text) {
        int length = text.length();
        int pos = 0;
        long whole = 0;
        while (pos < length && isDigit(text.charAt(pos))) {
            whole = whole * 10 + (text.charAt(pos) - '0');
            pos++;
            if (pos > MAX_WHOLE_DIGITS) {
                return OptionalLong.empty();
            }
        }
        if (pos == 0) {
            return OptionalLong.empty();
        }
        if (pos == length) {
            return OptionalLong.of(whole * 1000);
        }
        if (text.charAt(pos) != '.') {
            return OptionalLong.empty();
        }
        int fractionLength = length - pos - 1;
        if (fractionLength < 1 || fractionLength > FRACTION_DIGITS) {
            return OptionalLong.empty();
        }
        long fraction = 0;
        for (int i = pos + 1; i < length; i++) {
            char c = text.charAt(i);
            if (!isDigit(c)) {
                return OptionalLong.empty();
            }
            fraction = fraction * 10 + (c - '0');
        }
        for (int i = fractionLength; i < FRACTION_DIGITS; i++) {
            fraction *= 10;
        }
        return OptionalLong.of(whole * 1000 + fraction);
    }

    /** Formats milliseconds as whole seconds, a dot and exactly three digits. */
    public static String format(long millis) {
        long rest = millis % 1000;
        StringBuilder sb = new StringBuilder();
        sb.append(millis / 1000).append('.');
        if (rest < 100) {
            sb.append('0');
        }
        if (rest < 10) {
            sb.append('0');
        }
        sb.append(rest);
        return sb.toString();
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
