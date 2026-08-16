package com.addi.lead;

import static com.addi.lead.FixtureDir.ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.addi.lead.app.Config;
import com.addi.lead.domain.Checkpoint;
import com.addi.lead.domain.Decision;
import com.addi.lead.domain.Prospect;
import com.addi.lead.domain.RejectionCause;
import com.addi.lead.domain.Step;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The wired application, run in process against captured streams. No JVM is spawned. */
class MainTest {

    @TempDir
    Path dir;

    private record Result(int code, String out, String err) {}

    private Result run(String... args) {
        return run(new Config(Duration.ofSeconds(2), dir, OptionalLong.of(7)), args);
    }

    private static Result run(Config config, String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var code = Main.run(args, config, new PrintStream(out, true, UTF_8), new PrintStream(err, true, UTF_8));
        return new Result(code, out.toString(UTF_8), err.toString(UTF_8));
    }

    @Test
    void aCleanLeadConvertsAndExitsZero() {
        FixtureDir.clean(dir);

        var result = run("validate-lead", "--id", ID);

        assertEquals(0, result.code(), result.err());
        assertTrue(result.out().startsWith("Lead " + ID + " converted to prospect. Score "), result.out());
        assertTrue(result.err().isEmpty(), result.err());
    }

    @Test
    void anUnseededLeadIsRejectedAndExitsOne() {
        FixtureDir.clean(dir);

        var result = run("validate-lead", "--id", "9999999999");

        assertEquals(1, result.code());
        assertTrue(result.out().contains("not found in the local database"), result.out());
    }

    @Test
    void anIdWithNoRegistryRowExitsOne() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "registry.csv");

        var result = run("validate-lead", "--id", ID);

        assertEquals(1, result.code());
        assertTrue(result.out().contains("not found in the national registry"), result.out());
    }

    @Test
    void aJudicialRecordExitsOne() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "judicial.csv", ID + ",1,0,UP");

        var result = run("validate-lead", "--id", ID);

        assertEquals(1, result.code());
        assertTrue(result.out().contains("1 judicial records found"), result.out());
    }

    @Test
    void aSanctionsHitExitsOne() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "bureau.csv", ID + ",OFAC,0,UP");

        var result = run("validate-lead", "--id", ID);

        assertEquals(1, result.code());
        assertTrue(result.out().contains("sanctioned on the OFAC list"), result.out());
    }

    @Test
    void aDownBureauIsPendingAtTheBureauAndExitsTwo() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "bureau.csv", ID + ",,0,DOWN");

        var result = run("validate-lead", "--id", ID);

        assertEquals(2, result.code());
        assertTrue(result.out().contains("pending manual review at step BUREAU"), result.out());
    }

    @Test
    void aDownScoreIsPendingAtTheScoreAndExitsTwo() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "score.csv", ID + ",0,DOWN");

        var result = run("validate-lead", "--id", ID);

        assertEquals(2, result.code());
        assertTrue(result.out().contains("pending manual review at step SCORE"), result.out());
    }

    @Test
    void aRegistrySlowerThanTheTimeoutExitsTwo() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "registry.csv", ID + ",Ana,Restrepo,1990-03-14,10000,UP");

        var result = run(new Config(Duration.ofMillis(100), dir, OptionalLong.of(7)), "validate-lead", "--id", ID);

        assertEquals(2, result.code());
        assertTrue(result.out().contains("pending manual review at step REGISTRY"), result.out());
    }

    @Test
    void theFixturesTheProjectShipsConvertEndToEnd() {
        var result = run(Config.defaults(), "validate-lead", "--id", "1020304050");

        assertEquals(0, result.code(), result.err());
        assertTrue(result.out().contains("converted to prospect. Score "), result.out());
    }

    @Test
    void pendingManualReviewExitsTwo() {
        assertEquals(
                2,
                Main.exitCode(new Decision.PendingManualReview(
                        new Checkpoint(FixtureDir.LEAD, Step.BUREAU, "unavailable"))));
    }

    @Test
    void theOtherTwoDecisionsExitZeroAndOne() {
        assertEquals(0, Main.exitCode(new Decision.Converted(new Prospect(FixtureDir.LEAD, 75))));
        assertEquals(1, Main.exitCode(new Decision.Rejected(new RejectionCause.RegistryNotFound())));
    }

    @Test
    void aMissingIdExitsThreeWithUsageOnStderrAndNothingOnStdout() {
        var result = run("validate-lead");

        assertEquals(3, result.code());
        assertTrue(result.out().isEmpty(), result.out());
        assertTrue(result.err().contains("validate-lead --id"), result.err());
    }

    @Test
    void aMalformedIdExitsThree() {
        for (var token : new String[] {"", "   ", "12 34"}) {
            var result = run("validate-lead", "--id", token);

            assertEquals(3, result.code(), token);
            assertTrue(result.out().isEmpty(), token);
            assertFalse(result.err().isBlank(), token);
        }
    }

    @Test
    void anUnknownCommandExitsThreeWithUsageOnStderr() {
        var result = run("qualify-lead", "--id", ID);

        assertEquals(3, result.code());
        assertTrue(result.out().isEmpty(), result.out());
        assertTrue(result.err().contains("validate-lead --id"), result.err());
    }

    @Test
    void noArgumentsAtAllExitsThreeWithUsageOnStderr() {
        var result = run();

        assertEquals(3, result.code());
        assertTrue(result.out().isEmpty(), result.out());
        assertTrue(result.err().contains("validate-lead --id"), result.err());
    }
}
