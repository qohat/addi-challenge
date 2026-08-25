package com.addi.lead;

import static com.addi.lead.FixtureDir.ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The wired application, run in process against captured streams. No JVM is spawned. */
class MainTest {

    @TempDir
    Path dir;

    private record Result(int code, String out, String err) {}

    private Result run(String... args) {
        return run(config(dir), args);
    }

    /** Runtime state goes under the fixture directory, so a test never writes into the project. */
    private Config config(Path fixtures) {
        return new Config(Duration.ofSeconds(2), fixtures, OptionalLong.of(0), dir.resolve("data"), Duration.ofHours(24));
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
        assertEquals("Lead " + ID + " converted to prospect. Score 67.", result.out().strip());
        assertTrue(result.err().isEmpty(), result.err());
    }

    @Test
    void aLowScoringLeadIsRejectedAndExitsOne() {
        FixtureDir.clean(dir);

        var config = new Config(Duration.ofSeconds(2), dir, OptionalLong.of(7), dir.resolve("data"), Duration.ofHours(24));
        var result = run(config, "validate-lead", "--id", ID);

        assertEquals(1, result.code(), result.err());
        assertEquals("Lead " + ID + " rejected: qualification score 57 is not above 60.", result.out().strip());
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
    void aFlaggedLeadIsRejectedAndExitsOne() {
        var result = run(config(Config.defaults().fixtures()), "validate-lead", "--id", "1040506070");

        assertEquals(1, result.code(), result.err());
        assertEquals("Lead 1040506070 rejected: flagged by the fraud check.", result.out().strip());
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
    void aDownBureauOpensACaseAndNamesItBelowTheDecisionLine() throws IOException {
        downBureau();

        var result = run("validate-lead", "--id", ID);

        var lines = result.out().strip().lines().toList();
        assertEquals(2, result.code());
        assertEquals(2, lines.size(), result.out());
        assertTrue(lines.getFirst().contains("pending manual review at step BUREAU"), result.out());
        assertEquals("Case " + onlyCase().getFileName() + " opened.", lines.get(1));
        assertTrue(Files.readAllLines(onlyCase()).containsAll(List.of("status=OPEN", "pending=BUREAU")), "the case");
    }

    @Test
    void approvingResumesPastTheDownBureauAndConverts() throws IOException {
        downBureau();
        run("validate-lead", "--id", ID);
        var caseFile = onlyCase();

        var result = run("review", "resolve", caseFile.getFileName().toString(), "--approve");

        assertEquals(0, result.code(), result.err());
        assertEquals("Lead " + ID + " converted to prospect. Score 67.", result.out().strip());
        assertTrue(Files.readAllLines(caseFile).contains("status=APPROVED"), "the case");
        assertFalse(Files.exists(dir.resolve("data").resolve("bureau-cache.csv")), "an approval is not a bureau answer");
    }

    @Test
    void anApprovedFraudCaseResumesIntoTheScore() throws IOException {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "fraud.csv", ID + ",false,0,DOWN");
        var pending = run("validate-lead", "--id", ID);
        var caseFile = onlyCase();

        var result = run("review", "resolve", caseFile.getFileName().toString(), "--approve");

        assertEquals(2, pending.code(), pending.err());
        assertTrue(pending.out().contains("pending manual review at step FRAUD"), pending.out());
        assertEquals(0, result.code(), result.err());
        assertEquals("Lead " + ID + " converted to prospect. Score 67.", result.out().strip());
    }

    @Test
    void rejectingClosesTheCaseAndNamesItOnTheDecisionLine() throws IOException {
        downBureau();
        run("validate-lead", "--id", ID);
        var caseFile = onlyCase();
        var name = caseFile.getFileName().toString();

        var result = run("review", "resolve", name, "--reject");

        assertEquals(1, result.code());
        assertEquals(
                "Lead " + ID + " rejected: manual review case " + name + " was rejected by an analyst.",
                result.out().strip());
        assertTrue(Files.readAllLines(caseFile).contains("status=REJECTED"), "the case");
    }

    @Test
    void aResolvedCaseAndAnUnknownCaseAreBothInputErrors() throws IOException {
        downBureau();
        run("validate-lead", "--id", ID);
        var caseFile = onlyCase();
        run("review", "resolve", caseFile.getFileName().toString(), "--reject");
        var resolved = Files.readAllBytes(caseFile);

        var again = run("review", "resolve", caseFile.getFileName().toString(), "--approve");
        var unknown = run("review", "resolve", "no-such-case", "--approve");

        assertEquals(3, again.code());
        assertEquals(3, unknown.code());
        assertTrue(again.out().isEmpty(), again.out());
        assertArrayEquals(resolved, Files.readAllBytes(caseFile), "a resolved case was rewritten");
        assertFalse(Files.exists(reviewDir().resolve("no-such-case")), "an unknown case was written");
    }

    @Test
    void approvingIntoADownScoreOpensASecondCase() throws IOException {
        downBureau();
        FixtureDir.write(dir, "score.csv", ID + ",0,DOWN");
        run("validate-lead", "--id", ID);
        var first = onlyCase();

        var result = run("review", "resolve", first.getFileName().toString(), "--approve");

        assertEquals(2, result.code());
        var cases = cases();
        assertEquals(2, cases.size(), cases.toString());
        assertTrue(Files.readAllLines(first).contains("status=APPROVED"), "the first case");
        var second = cases.stream().filter(file -> !file.equals(first)).findFirst().orElseThrow();
        assertTrue(Files.readAllLines(second).containsAll(List.of("status=OPEN", "pending=SCORE")), "the second case");
    }

    @Test
    void aCaseThatCannotBeWrittenExitsFourOnStderr() throws IOException {
        downBureau();
        Files.createDirectories(dir.resolve("data"));
        Files.writeString(reviewDir(), "a regular file where the queue goes");

        var result = run("validate-lead", "--id", ID);

        assertEquals(4, result.code());
        assertFalse(result.err().isBlank());
        assertTrue(result.out().contains("pending manual review"), result.out());
    }

    private void downBureau() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "bureau.csv", ID + ",,0,DOWN");
    }

    private Path reviewDir() {
        return dir.resolve("data").resolve("review");
    }

    private Path onlyCase() throws IOException {
        var cases = cases();
        assertEquals(1, cases.size(), cases.toString());
        return cases.getFirst();
    }

    private List<Path> cases() throws IOException {
        try (var files = Files.list(reviewDir())) {
            return files.sorted().toList();
        }
    }

    @Test
    void aRegistrySlowerThanTheTimeoutExitsTwo() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "registry.csv", ID + ",Ana,Restrepo,1990-03-14,10000,UP");

        var config = new Config(Duration.ofMillis(100), dir, OptionalLong.of(7), dir.resolve("data"), Duration.ofHours(24));
        var result = run(config, "validate-lead", "--id", ID);

        assertEquals(2, result.code());
        assertTrue(result.out().contains("pending manual review at step REGISTRY"), result.out());
    }

    @Test
    void theFixturesTheProjectShipsConvertEndToEnd() {
        var result = run(config(Config.defaults().fixtures()), "validate-lead", "--id", "1020304050");

        assertEquals(0, result.code(), result.err());
        assertTrue(result.out().contains("converted to prospect. Score "), result.out());
    }

    /** Every demo path is also reachable by hand, which is why the leads live in the shipped fixtures. */
    @Test
    void theLeadsTheDemoUsesAreReachableOneByOne() {
        var expected = Map.of(
                "1050607080", "not found in the national registry",
                "1060708090", "national registry data does not match on birthDate",
                "1070809000", "2 judicial records found",
                "1080900010", "sanctioned on the OFAC list",
                "1040506070", "flagged by the fraud check",
                "1090001020", "pending manual review at step BUREAU",
                "1100102030", "pending manual review at step REGISTRY");

        for (var lead : expected.entrySet()) {
            var result = run(config(Config.defaults().fixtures()), "validate-lead", "--id", lead.getKey());

            assertTrue(result.out().contains(lead.getValue()), lead.getKey() + ": " + result.out());
        }
    }

    @Test
    void aCachedBureauAnswerOutlivesTheBureauItself() throws IOException {
        FixtureDir.clean(dir);
        var first = run("validate-lead", "--id", ID);

        Files.delete(dir.resolve("bureau.csv"));
        var second = run("validate-lead", "--id", ID);

        assertEquals(0, first.code(), first.err());
        assertEquals(0, second.code(), second.err());
        assertEquals(first.out(), second.out(), "the cache changed the decision it served");
    }

    @Test
    void anEntryOlderThanTheTtlIsIgnoredAndTheRunDecidesFromTheFixture() throws IOException {
        FixtureDir.clean(dir);
        Files.createDirectories(dir.resolve("data"));
        Files.writeString(
                dir.resolve("data").resolve("bureau-cache.csv"),
                "id,recordedAt,outcome,list\n" + ID + ",2020-01-01T00:00:00Z,SANCTIONED,OFAC\n");

        var result = run("validate-lead", "--id", ID);

        assertEquals(0, result.code(), result.out());
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
