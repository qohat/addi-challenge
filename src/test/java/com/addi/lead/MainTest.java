package com.addi.lead;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.addi.lead.domain.Checkpoint;
import com.addi.lead.domain.Decision;
import com.addi.lead.domain.Email;
import com.addi.lead.domain.Lead;
import com.addi.lead.domain.NationalId;
import com.addi.lead.domain.Prospect;
import com.addi.lead.domain.RejectionCause;
import com.addi.lead.domain.Step;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The wired application, run in process against captured streams. No JVM is spawned. */
class MainTest {

    private static final Lead LEAD = new Lead(
            new NationalId("1020304050"),
            "Ana",
            "Restrepo",
            LocalDate.of(1990, 3, 14),
            new Email("ana.restrepo@example.com"));

    private record Result(int code, String out, String err) {}

    private static Result run(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var code = Main.run(args, new PrintStream(out, true, UTF_8), new PrintStream(err, true, UTF_8));
        return new Result(code, out.toString(UTF_8), err.toString(UTF_8));
    }

    @Test
    void aSeededLeadConvertsAndExitsZero() {
        var result = run("validate-lead", "--id", "1020304050");

        assertEquals(0, result.code());
        assertEquals("Lead 1020304050 converted to prospect. Score 75." + System.lineSeparator(), result.out());
        assertTrue(result.err().isEmpty(), result.err());
    }

    @Test
    void anUnseededLeadIsRejectedAndExitsOne() {
        var result = run("validate-lead", "--id", "9999999999");

        assertEquals(1, result.code());
        assertTrue(result.out().contains("not found in the local database"), result.out());
    }

    @Test
    void pendingManualReviewExitsTwo() {
        assertEquals(
                2, Main.exitCode(new Decision.PendingManualReview(new Checkpoint(LEAD, Step.BUREAU, "unavailable"))));
    }

    @Test
    void theOtherTwoDecisionsExitZeroAndOne() {
        assertEquals(0, Main.exitCode(new Decision.Converted(new Prospect(LEAD, 75))));
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
        var result = run("qualify-lead", "--id", "1020304050");

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
