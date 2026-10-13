package io.github.gulliby.shardplan.cli;

import io.github.gulliby.shardplan.plan.Seconds;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * The parsed command line. Unused numbers are 0; files is null without
 * {@code --files}; timings is the raw TIMINGS argument.
 */
public record Args(String command, int shards, int index, long targetMillis, String files, String timings) {

    private static final int MAX_SHARDS = 64;
    private static final int MAX_COUNT_DIGITS = 2;

    private static final String PLAN = "plan";
    private static final String LIST = "list";
    private static final String NEED = "need";

    private static final String FILES = "--files";
    private static final String SHARDS = "--shards";
    private static final String INDEX = "--index";
    private static final String TARGET = "--target";

    /**
     * Parses the arguments left to right; every usage error throws
     * IllegalArgumentException with the text that follows the program name.
     */
    public static Args parse(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("missing command (plan, list or need)");
        }
        String command = args[0];
        if (!command.equals(PLAN) && !command.equals(LIST) && !command.equals(NEED)) {
            throw new IllegalArgumentException("unknown command \"" + command + "\"");
        }
        String files = null;
        String shardsText = null;
        String indexText = null;
        String targetText = null;
        List<String> positional = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-") || !arg.startsWith("-")) {
                positional.add(arg);
                continue;
            }
            int eq = arg.indexOf('=');
            String name = eq < 0 ? arg : arg.substring(0, eq);
            if (!name.equals(FILES) && !name.equals(SHARDS) && !name.equals(INDEX) && !name.equals(TARGET)) {
                throw new IllegalArgumentException("unknown option \"" + arg + "\"");
            }
            if (!takes(command, name)) {
                throw new IllegalArgumentException(command + " does not take " + name);
            }
            String value;
            if (eq >= 0) {
                value = arg.substring(eq + 1);
            } else {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(name + " needs a value");
                }
                i++;
                value = args[i];
            }
            if (name.equals(FILES)) {
                files = value;
            } else if (name.equals(SHARDS)) {
                shardsText = value;
            } else if (name.equals(INDEX)) {
                indexText = value;
            } else {
                targetText = value;
            }
        }
        int shards = 0;
        int index = 0;
        long targetMillis = 0;
        if (command.equals(NEED)) {
            if (targetText == null) {
                throw new IllegalArgumentException("--target is required");
            }
            OptionalLong target = Seconds.parse(targetText);
            if (target.isEmpty() || target.getAsLong() <= 0) {
                throw new IllegalArgumentException("invalid --target \"" + targetText + "\"");
            }
            targetMillis = target.getAsLong();
        } else {
            if (shardsText == null) {
                throw new IllegalArgumentException("--shards is required");
            }
            shards = count(shardsText, MAX_SHARDS);
            if (shards == 0) {
                throw new IllegalArgumentException("invalid --shards \"" + shardsText + "\"");
            }
            if (command.equals(LIST)) {
                if (indexText == null) {
                    throw new IllegalArgumentException("--index is required");
                }
                index = count(indexText, shards);
                if (index == 0) {
                    throw new IllegalArgumentException(
                            "invalid --index \"" + indexText + "\" (1 to " + shards + ")");
                }
            }
        }
        if (positional.isEmpty()) {
            throw new IllegalArgumentException("missing TIMINGS");
        }
        if (positional.size() > 1) {
            throw new IllegalArgumentException("too many arguments");
        }
        return new Args(command, shards, index, targetMillis, files, positional.get(0));
    }

    /** Whether the command takes the option; --files is taken by all three. */
    private static boolean takes(String command, String name) {
        if (name.equals(SHARDS)) {
            return !command.equals(NEED);
        }
        if (name.equals(INDEX)) {
            return command.equals(LIST);
        }
        if (name.equals(TARGET)) {
            return command.equals(NEED);
        }
        return true;
    }

    /** 1 or 2 ASCII digits with a value from 1 to max; 0 when not valid. */
    private static int count(String text, int max) {
        int length = text.length();
        if (length < 1 || length > MAX_COUNT_DIGITS) {
            return 0;
        }
        int value = 0;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return 0;
            }
            value = value * 10 + (c - '0');
        }
        return value >= 1 && value <= max ? value : 0;
    }
}
