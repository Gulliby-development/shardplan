package io.github.gulliby.shardplan;

import io.github.gulliby.shardplan.cli.Cli;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** The process entry of shardplan. */
public final class Main {

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), false, StandardCharsets.UTF_8);
        int code = Cli.run(args, System.in, out, err);
        out.flush();
        err.flush();
        System.exit(code);
    }
}
