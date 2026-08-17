package com.addi.lead;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.addi.lead.app.Config;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The demo against the fixtures the project ships, run once for the whole class because it pays the
 * real latencies. Elapsed millis are blanked out of the transcript; everything else is asserted as
 * written.
 */
class DemoTest {

    private static final List<String> TITLES = List.of(
            "A clean lead converts",
            "The lead is not in the local database",
            "The lead is not in the national registry",
            "The registry data does not match",
            "Judicial records are found",
            "The lead is on a sanctions list",
            "The qualification score is too low",
            "The bureau is down, an analyst approves, the lead converts",
            "The bureau is down, an analyst rejects",
            "The registry does not answer in time",
            "The bureau cache serves the second run");

    private static int code;
    private static String out;
    private static String err;
    private static List<String> before;
    private static List<String> after;

    @BeforeAll
    static void runTheDemo() throws IOException {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        before = projectData();
        code = Main.run(
                new String[] {"demo"},
                Config.defaults(),
                new PrintStream(stdout, true, UTF_8),
                new PrintStream(stderr, true, UTF_8));
        after = projectData();
        out = stdout.toString(UTF_8).replaceAll(" in \\d+ms", "");
        err = stderr.toString(UTF_8);
    }

    @Test
    void runsElevenScenariosInOrderAndExitsZero() {
        assertEquals(0, code, err);
        assertTrue(err.isEmpty(), err);
        var headers = out.lines().filter(line -> line.startsWith("== ")).toList();
        assertEquals(TITLES.size(), headers.size(), out);
        for (var i = 0; i < TITLES.size(); i++) {
            assertEquals("== " + (i + 1) + ". " + TITLES.get(i), headers.get(i));
        }
    }

    @Test
    void showsTheRenderedLineOfEveryRejectionCause() {
        assertLine(2, "Lead 9999999999 rejected: not found in the local database.");
        assertLine(3, "Lead 1050607080 rejected: not found in the national registry.");
        assertLine(4, "Lead 1060708090 rejected: national registry data does not match on birthDate.");
        assertLine(5, "Lead 1070809000 rejected: 2 judicial records found.");
        assertLine(6, "Lead 1080900010 rejected: sanctioned on the OFAC list.");
        assertLine(7, "Lead 1030405060 rejected: qualification score 57 is not above 60.");
        assertTrue(block(9).contains("was rejected by an analyst."), block(9));
    }

    @Test
    void theTwoSeedsDecideTheScoreAndAnApprovedCaseResumesIntoIt() {
        assertLine(1, "Lead 1020304050 converted to prospect. Score 67.");
        var lines = block(8).lines().toList();
        var resolve = lines.stream()
                .filter(line -> line.startsWith("$ review resolve "))
                .findFirst()
                .orElseThrow(() -> new AssertionError(block(8)));
        assertTrue(resolve.startsWith("$ review resolve 1090001020-"), resolve);
        assertEquals("Lead 1090001020 converted to prospect. Score 67.", lines.get(lines.indexOf(resolve) + 1));
    }

    @Test
    void showsTheTimeoutTheAnalystRejectionAndTheCacheHit() {
        assertLine(10, "Lead 1100102030 pending manual review at step REGISTRY: timeout after 2000ms.");
        assertTrue(block(10).contains("Case 1100102030-"), block(10));
        assertTrue(block(10).contains("exit 2"), block(10));
        assertTrue(block(9).contains("exit 1"), block(9));
        var served = block(11).lines().filter(line -> line.contains("converted to prospect")).toList();
        assertEquals(2, served.size(), block(11));
        assertEquals(served.getFirst(), served.getLast(), "the cache changed the decision it served");
    }

    @Test
    void writesNothingIntoTheProjectDataDirectory() {
        assertEquals(before, after);
    }

    private static void assertLine(int scenario, String expected) {
        assertTrue(block(scenario).lines().anyMatch(expected::equals), block(scenario));
    }

    private static String block(int scenario) {
        return Arrays.stream(out.split("(?m)^== "))
                .filter(part -> part.startsWith(scenario + ". "))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No scenario " + scenario + " in:\n" + out));
    }

    /** The demo runs in a temp directory, so the project's own runtime state must come out untouched. */
    private static List<String> projectData() throws IOException {
        var data = Config.defaults().data();
        if (!Files.exists(data)) {
            return List.of();
        }
        try (var files = Files.walk(data)) {
            return files.map(Path::toString).sorted().toList();
        }
    }
}
