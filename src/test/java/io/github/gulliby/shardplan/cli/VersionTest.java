package io.github.gulliby.shardplan.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.shardplan.Main;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VersionTest {

    private record Result(int code, String stdout, String stderr) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(stdout, false, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(stderr, false, StandardCharsets.UTF_8);
        int code = Cli.run(args, new ByteArrayInputStream(new byte[0]), out, err);
        out.flush();
        err.flush();
        return new Result(
                code,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    @Test
    void versionPrintsDevelopmentVersion() {
        Result result = run("--version");

        assertEquals(0, result.code());
        assertEquals("shardplan dev\n", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void versionWithOtherArgumentsUsesExistingErrors() {
        Result unknownCommand = run("--version", "plan");
        assertEquals(2, unknownCommand.code());
        assertEquals("", unknownCommand.stdout());
        assertEquals("shardplan: unknown command \"--version\"\n", unknownCommand.stderr());

        Result unknownOption = run("plan", "--shards", "2", "--version");
        assertEquals(2, unknownOption.code());
        assertEquals("", unknownOption.stdout());
        assertEquals("shardplan: unknown option \"--version\"\n", unknownOption.stderr());
    }

    @Test
    void pomConfiguresExecutableJarManifest() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<mainClass>" + Main.class.getName() + "</mainClass>"));
        assertTrue(pom.contains("<addDefaultImplementationEntries>true</addDefaultImplementationEntries>"));
    }
}
