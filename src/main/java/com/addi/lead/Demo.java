package com.addi.lead;

import com.addi.lead.app.Config;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;

/**
 * Every outcome the system can produce, as real commands against the shipped fixtures. Each one goes
 * through {@link Main#run}, so the demo cannot drift from the CLI a reviewer types: a path it cannot
 * reach is a missing fixture row and never a reason to touch business code.
 */
final class Demo {

    /** The only lever on the score, which is never fixtured (ADR 0006): seed 0 draws 67, seed 7 draws 57. */
    private static final long CONVERTS = 0;

    private static final long TOO_LOW = 7;

    /** The case the scenario's first command opened, substituted once the file exists. */
    private static final String CASE = "<case>";

    private record Scenario(String title, long seed, List<List<String>> commands) {}

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("A clean lead converts", CONVERTS, List.of(validate("1020304050"))),
            new Scenario("The lead is not in the local database", CONVERTS, List.of(validate("9999999999"))),
            new Scenario("The lead is not in the national registry", CONVERTS, List.of(validate("1050607080"))),
            new Scenario("The registry data does not match", CONVERTS, List.of(validate("1060708090"))),
            new Scenario("Judicial records are found", CONVERTS, List.of(validate("1070809000"))),
            new Scenario("The lead is on a sanctions list", CONVERTS, List.of(validate("1080900010"))),
            new Scenario("The qualification score is too low", TOO_LOW, List.of(validate("1030405060"))),
            new Scenario(
                    "The bureau is down, an analyst approves, the lead converts",
                    CONVERTS,
                    List.of(validate("1090001020"), resolve("--approve"))),
            new Scenario(
                    "The bureau is down, an analyst rejects",
                    CONVERTS,
                    List.of(validate("1090001020"), resolve("--reject"))),
            new Scenario("The registry does not answer in time", CONVERTS, List.of(validate("1100102030"))),
            new Scenario(
                    "The bureau cache serves the second run",
                    CONVERTS,
                    List.of(validate("1020304050"), validate("1020304050"))));

    /**
     * A temp directory per run and a numbered one per scenario, so the demo neither reads nor writes
     * the reviewer's own cache and queue, and no scenario can depend on the order they run in.
     */
    static int run(Config config, PrintStream out, PrintStream err) {
        try {
            var root = Files.createTempDirectory("lead-demo");
            out.println("Demo data directory: " + root);
            for (var number = 1; number <= SCENARIOS.size(); number++) {
                scenario(SCENARIOS.get(number - 1), number, root.resolve(String.valueOf(number)), config, out, err);
            }
            return 0;
        } catch (IOException e) {
            err.println("The demo could not run: " + e);
            return 4;
        }
    }

    private static void scenario(Scenario scenario, int number, Path data, Config config, PrintStream out, PrintStream err)
            throws IOException {
        out.println();
        out.println("== " + number + ". " + scenario.title());
        for (var command : scenario.commands()) {
            var args = resolved(command, data);
            out.println("$ " + String.join(" ", args));
            var started = System.nanoTime();
            var code = Main.run(args, scenarioConfig(config, scenario.seed(), data), out, err);
            // The elapsed time is the only place the cache hit and the timeout are visible.
            out.println("exit " + code + " in " + (System.nanoTime() - started) / 1_000_000 + "ms");
        }
    }

    private static Config scenarioConfig(Config config, long seed, Path data) {
        return new Config(
                config.parallelTimeout(), config.fixtures(), OptionalLong.of(seed), data, config.bureauCacheTtl());
    }

    private static String[] resolved(List<String> command, Path data) throws IOException {
        var opened = command.contains(CASE) ? onlyCase(data) : CASE;
        return command.stream()
                .map(token -> CASE.equals(token) ? opened : token)
                .toArray(String[]::new);
    }

    private static String onlyCase(Path data) throws IOException {
        try (var files = Files.list(data.resolve("review"))) {
            var cases = files.map(file -> file.getFileName().toString()).toList();
            if (cases.size() != 1) {
                throw new IOException("Expected one review case under " + data + ", found " + cases);
            }
            return cases.getFirst();
        }
    }

    private static List<String> validate(String id) {
        return List.of("validate-lead", "--id", id);
    }

    private static List<String> resolve(String resolution) {
        return List.of("review", "resolve", CASE, resolution);
    }

    private Demo() {}
}
