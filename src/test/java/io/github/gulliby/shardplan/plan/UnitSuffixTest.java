package io.github.gulliby.shardplan.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.shardplan.cli.Args;
import io.github.gulliby.shardplan.input.TimingsParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class UnitSuffixTest {

    @Test
    void parseAcceptsASecondsSuffix() {
        assertEquals(OptionalLong.of(12000), Seconds.parse("12s"), "12s");
        assertEquals(OptionalLong.of(500), Seconds.parse("0.5s"), "0.5s");
        assertEquals(OptionalLong.of(3250), Seconds.parse("3.250s"), "3.250s");
        assertEquals(OptionalLong.of(99999999), Seconds.parse("99999.999s"), "99999.999s");
        assertEquals(OptionalLong.of(0), Seconds.parse("0s"), "0s");
        assertEquals(OptionalLong.of(12500), Seconds.parse("00012.5s"), "00012.5s");
    }

    @Test
    void parseAcceptsWholeMilliseconds() {
        assertEquals(OptionalLong.of(250), Seconds.parse("250ms"), "250ms");
        assertEquals(OptionalLong.of(1500), Seconds.parse("1500ms"), "1500ms");
        assertEquals(OptionalLong.of(0), Seconds.parse("0ms"), "0ms");
        assertEquals(OptionalLong.of(1), Seconds.parse("1ms"), "1ms");
        assertEquals(OptionalLong.of(99999999), Seconds.parse("99999999ms"), "99999999ms");
        assertEquals(OptionalLong.of(12), Seconds.parse("00000012ms"), "00000012ms");
    }

    @Test
    void parseRejectsOtherUnitsAndShapes() {
        assertEquals(OptionalLong.empty(), Seconds.parse("ms"), "[ms]");
        assertEquals(OptionalLong.empty(), Seconds.parse("s"), "[s]");
        assertEquals(OptionalLong.empty(), Seconds.parse("1.5ms"), "[1.5ms]");
        assertEquals(OptionalLong.empty(), Seconds.parse(".5s"), "[.5s]");
        assertEquals(OptionalLong.empty(), Seconds.parse("5.s"), "[5.s]");
        assertEquals(OptionalLong.empty(), Seconds.parse("1m"), "[1m]");
        assertEquals(OptionalLong.empty(), Seconds.parse("12S"), "[12S]");
        assertEquals(OptionalLong.empty(), Seconds.parse("12MS"), "[12MS]");
        assertEquals(OptionalLong.empty(), Seconds.parse("12Ms"), "[12Ms]");
        assertEquals(OptionalLong.empty(), Seconds.parse("12mss"), "[12mss]");
        assertEquals(OptionalLong.empty(), Seconds.parse("12sms"), "[12sms]");
        assertEquals(OptionalLong.empty(), Seconds.parse("1ss"), "[1ss]");
        assertEquals(OptionalLong.empty(), Seconds.parse("100000000ms"), "[100000000ms]");
        assertEquals(OptionalLong.empty(), Seconds.parse("000000012ms"), "[000000012ms]");
        assertEquals(OptionalLong.empty(), Seconds.parse("123456s"), "[123456s]");
        assertEquals(OptionalLong.empty(), Seconds.parse("1.2345s"), "[1.2345s]");
        assertEquals(OptionalLong.empty(), Seconds.parse("+5ms"), "[+5ms]");
        assertEquals(OptionalLong.empty(), Seconds.parse("-5ms"), "[-5ms]");
        assertEquals(OptionalLong.empty(), Seconds.parse("1e3ms"), "[1e3ms]");
        assertEquals(OptionalLong.empty(), Seconds.parse("1,5s"), "[1,5s]");
        assertEquals(OptionalLong.empty(), Seconds.parse("1.5 s"), "[1.5 s]");
    }

    @Test
    void aTimingsFileMayMixPlainAndUnitTimes() {
        LinkedHashMap<String, Long> timings =
                TimingsParser.parseTimings("t.txt", "1500ms a\n0.5s b\n2 c\n3.250 d\n");

        assertEquals(List.of("a", "b", "c", "d"), new ArrayList<>(timings.keySet()));
        assertEquals(List.of(1500L, 500L, 2000L, 3250L), new ArrayList<>(timings.values()));
    }

    @Test
    void anInvalidTimeIsReportedWithItsWholeText() {
        IllegalArgumentException first = assertThrows(IllegalArgumentException.class,
                () -> TimingsParser.parseTimings("e.txt", "12S a"));
        IllegalArgumentException second = assertThrows(IllegalArgumentException.class,
                () -> TimingsParser.parseTimings("e.txt", "1 a\n1.5ms b"));

        assertEquals("e.txt:1: invalid seconds \"12S\"", first.getMessage());
        assertEquals("e.txt:2: invalid seconds \"1.5ms\"", second.getMessage());
        assertEquals("1.500", Seconds.format(1500));
    }

    @Test
    void theTargetOptionTakesTheSameUnits() {
        assertEquals(45000, Args.parse(new String[] {"need", "--target", "45", "t.txt"}).targetMillis());
        assertEquals(45000, Args.parse(new String[] {"need", "--target", "45s", "t.txt"}).targetMillis());
        assertEquals(45000, Args.parse(new String[] {"need", "--target", "45000ms", "t.txt"}).targetMillis());
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> Args.parse(new String[] {"need", "--target", "0ms", "t.txt"}));
        assertEquals("invalid --target \"0ms\"", zero.getMessage());
    }
}
