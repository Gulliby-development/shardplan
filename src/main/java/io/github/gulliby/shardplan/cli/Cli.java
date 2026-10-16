package io.github.gulliby.shardplan.cli;

import io.github.gulliby.shardplan.input.TimingsParser;
import io.github.gulliby.shardplan.plan.Model;
import io.github.gulliby.shardplan.plan.Planner;
import io.github.gulliby.shardplan.plan.Seconds;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/** The command line: reads the input, runs one command and writes the text. */
public final class Cli {

    private static final String USAGE =
            "usage: shardplan plan --shards N [--files LIST] TIMINGS\n"
            + "       shardplan list --shards N --index K [--null] [--files LIST] TIMINGS\n"
            + "       shardplan need --target SECONDS [--max-shards N] [--files LIST] TIMINGS\n";

    private static final String PREFIX = "shardplan: ";
    private static final String STDIN_ARG = "-";

    private static final int EXIT_OK = 0;
    private static final int EXIT_NO_PLAN = 1;
    private static final int EXIT_ERROR = 2;

    private Cli() {
    }

    /** Runs the program and returns its exit code; stdout is written only on success. */
    public static int run(String[] args, InputStream stdin, PrintStream out, PrintStream err) {
        if (args.length == 1 && "--help".equals(args[0])) {
            out.print(USAGE);
            return EXIT_OK;
        }
        try {
            Args parsed = Args.parse(args);
            boolean fromStdin = parsed.timings().equals(STDIN_ARG);
            String display = fromStdin ? "<stdin>" : displayName(parsed.timings());
            String text = decode(display, readBytes(display, fromStdin ? null : parsed.timings(), stdin));
            LinkedHashMap<String, Long> timings = TimingsParser.parseTimings(display, text);
            List<Model.Entry> entries;
            String summary = null;
            if (parsed.files() != null) {
                String listDisplay = displayName(parsed.files());
                String listText = decode(listDisplay, readBytes(listDisplay, parsed.files(), stdin));
                List<String> paths = TimingsParser.parsePaths(listDisplay, listText);
                entries = Planner.estimate(paths, timings);
                summary = summary(entries, paths, timings);
            } else {
                if (timings.isEmpty()) {
                    throw new IllegalArgumentException(display + ": no files");
                }
                entries = new ArrayList<>();
                for (Map.Entry<String, Long> timing : timings.entrySet()) {
                    entries.add(new Model.Entry(timing.getKey(), timing.getValue(), false));
                }
            }
            if (parsed.command().equals("need")) {
                return need(entries, parsed.targetMillis(), parsed.maxShards(), out, err);
            }
            List<Model.Shard> shards = Planner.plan(entries, parsed.shards());
            if (parsed.command().equals("list")) {
                out.print(listText(shards.get(parsed.index() - 1), parsed.nullSeparated()));
            } else {
                out.print(planText(shards, summary));
            }
            return EXIT_OK;
        } catch (IllegalArgumentException e) {
            err.print(PREFIX + e.getMessage() + "\n");
            return EXIT_ERROR;
        }
    }

    private static String displayName(String arg) {
        return arg.replace('\\', '/');
    }

    /** Reads the file at the path, or standard input when the path is null. */
    private static byte[] readBytes(String display, String path, InputStream stdin) {
        try {
            return path == null ? stdin.readAllBytes() : Files.readAllBytes(Path.of(path));
        } catch (IOException | InvalidPathException e) {
            throw new IllegalArgumentException(display + ": cannot read the file");
        }
    }

    /** Decodes strictly: malformed input is an error and is never replaced. */
    private static String decode(String display, byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(display + ": input is not valid UTF-8");
        }
    }

    /** The last line of plan with --files: estimated files and ignored timings. */
    private static String summary(List<Model.Entry> entries, List<String> paths, Map<String, Long> timings) {
        int estimated = 0;
        for (Model.Entry entry : entries) {
            if (entry.estimated()) {
                estimated++;
            }
        }
        Set<String> listed = new HashSet<>(paths);
        int ignored = 0;
        for (String path : timings.keySet()) {
            if (!listed.contains(path)) {
                ignored++;
            }
        }
        return "estimated: " + estimated + ", ignored: " + ignored + "\n";
    }

    private static String planText(List<Model.Shard> shards, String summary) {
        StringBuilder sb = new StringBuilder();
        Model.Shard shortest = shards.get(0);
        for (Model.Shard shard : shards) {
            int count = shard.entries().size();
            sb.append("shard ").append(shard.index()).append(": ")
                    .append(Seconds.format(shard.millis())).append(" s, ")
                    .append(count).append(count == 1 ? " file" : " files").append('\n');
            for (Model.Entry entry : shard.entries()) {
                sb.append("  ").append(Seconds.format(entry.millis())).append(' ').append(entry.path());
                if (entry.estimated()) {
                    sb.append(" (estimated)");
                }
                sb.append('\n');
            }
            if (shard.millis() < shortest.millis()) {
                shortest = shard;
            }
        }
        Model.Shard longest = longestShard(shards);
        sb.append("balance: longest ").append(Seconds.format(longest.millis()))
                .append(" s (shard ").append(longest.index()).append("), shortest ")
                .append(Seconds.format(shortest.millis()))
                .append(" s (shard ").append(shortest.index()).append(")\n");
        if (summary != null) {
            sb.append(summary);
        }
        return sb.toString();
    }

    private static String listText(Model.Shard shard, boolean nullSeparated) {
        StringBuilder sb = new StringBuilder();
        char separator = nullSeparated ? '\0' : '\n';
        for (Model.Entry entry : shard.entries()) {
            sb.append(entry.path()).append(separator);
        }
        return sb.toString();
    }

    private static int need(List<Model.Entry> entries, long targetMillis, int maxShards, PrintStream out, PrintStream err) {
        String target = Seconds.format(targetMillis);
        Model.Entry longestFile = entries.get(0);
        for (Model.Entry entry : entries) {
            boolean longer = entry.millis() > longestFile.millis();
            boolean tieFirst = entry.millis() == longestFile.millis()
                    && entry.path().compareTo(longestFile.path()) < 0;
            if (longer || tieFirst) {
                longestFile = entry;
            }
        }
        if (longestFile.millis() > targetMillis) {
            err.print(PREFIX + longestFile.path() + " takes " + Seconds.format(longestFile.millis())
                    + " s, longer than the target " + target + " s\n");
            return EXIT_NO_PLAN;
        }
        OptionalInt needed = Planner.needed(entries, targetMillis);
        if (needed.isEmpty() || needed.getAsInt() > maxShards) {
            err.print(PREFIX + "the target " + target + " s needs more than " + maxShards
                    + (maxShards == 1 ? " shard\n" : " shards\n"));
            return EXIT_NO_PLAN;
        }
        Model.Shard longest = longestShard(Planner.plan(entries, needed.getAsInt()));
        out.print("shards needed: " + needed.getAsInt() + " (longest "
                + Seconds.format(longest.millis()) + " s)\n");
        return EXIT_OK;
    }

    /** The shard with the largest total; the lowest shard number wins a tie. */
    private static Model.Shard longestShard(List<Model.Shard> shards) {
        Model.Shard longest = shards.get(0);
        for (Model.Shard shard : shards) {
            if (shard.millis() > longest.millis()) {
                longest = shard;
            }
        }
        return longest;
    }
}
