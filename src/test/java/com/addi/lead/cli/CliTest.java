package com.addi.lead.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.addi.lead.domain.CaseId;
import com.addi.lead.domain.CaseStatus;
import com.addi.lead.domain.Checkpoint;
import com.addi.lead.domain.Decision;
import com.addi.lead.domain.Email;
import com.addi.lead.domain.Lead;
import com.addi.lead.domain.NationalId;
import com.addi.lead.domain.Prospect;
import com.addi.lead.domain.RejectionCause;
import com.addi.lead.domain.Step;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Parsing and rendering are pure functions, so the CLI is tested without spawning a JVM. */
class CliTest {

    private static final NationalId ID = new NationalId("1020304050");

    private static final Lead LEAD =
            new Lead(ID, "Ana", "Restrepo", LocalDate.of(1990, 3, 14), new Email("ana.restrepo@example.com"));

    @Test
    void parsesTheOnlyCommand() {
        assertEquals(
                new Cli.Invocation.ValidateLead(ID), Cli.parse(new String[] {"validate-lead", "--id", "1020304050"}));
    }

    @Test
    void rejectsAMissingIdFlag() {
        assertInstanceOf(Cli.Invocation.InputError.class, Cli.parse(new String[] {"validate-lead"}));
    }

    @Test
    void rejectsAnIdThatIsEmptyOrCarriesWhitespace() {
        for (var token : List.of("", "   ", "12 34")) {
            assertInstanceOf(
                    Cli.Invocation.InputError.class, Cli.parse(new String[] {"validate-lead", "--id", token}), token);
        }
    }

    @Test
    void parsesTheTwoResolutionsOfACase() {
        assertEquals(
                new Cli.Invocation.ResolveReview(new CaseId("X"), CaseStatus.APPROVED),
                Cli.parse(new String[] {"review", "resolve", "X", "--approve"}));
        assertEquals(
                new Cli.Invocation.ResolveReview(new CaseId("X"), CaseStatus.REJECTED),
                Cli.parse(new String[] {"review", "resolve", "X", "--reject"}));
    }

    @Test
    void rejectsEveryOtherShapeOfReview() {
        var shapes = List.of(
                new String[] {"review"},
                new String[] {"review", "resolve", "X"},
                new String[] {"review", "resolve", "X", "--maybe"},
                new String[] {"review", "list"});
        for (var shape : shapes) {
            assertInstanceOf(Cli.Invocation.InputError.class, Cli.parse(shape), String.join(" ", shape));
        }
    }

    @Test
    void parsesTheDemoAndNothingAfterIt() {
        assertEquals(new Cli.Invocation.Demo(), Cli.parse(new String[] {"demo"}));
        for (var shape : List.of(new String[] {"demo", "--all"}, new String[] {"demo", "x"})) {
            assertInstanceOf(Cli.Invocation.InputError.class, Cli.parse(shape), String.join(" ", shape));
        }
    }

    @Test
    void namesEveryCommandInTheUsage() {
        for (var command : List.of("validate-lead", "review resolve", "demo")) {
            assertTrue(Cli.USAGE.contains(command), Cli.USAGE);
        }
    }

    @Test
    void rejectsAnUnknownCommand() {
        assertInstanceOf(Cli.Invocation.InputError.class, Cli.parse(new String[] {"qualify-lead", "--id", "1"}));
    }

    @Test
    void rejectsNoArgumentsAtAll() {
        assertInstanceOf(Cli.Invocation.InputError.class, Cli.parse(new String[] {}));
    }

    @Test
    void namesTheScoreOnTheConversionLine() {
        assertEquals(
                "Lead 1020304050 converted to prospect. Score 75.",
                Cli.render(ID, new Decision.Converted(new Prospect(LEAD, 75))));
    }

    @Test
    void distinguishesTheLookupMissFromARegistryMiss() {
        assertEquals(
                "Lead 1020304050 rejected: not found in the local database.",
                Cli.render(ID, new Decision.Rejected(new RejectionCause.LeadNotInDatabase(ID))));
        assertEquals(
                "Lead 1020304050 rejected: not found in the national registry.",
                Cli.render(ID, new Decision.Rejected(new RejectionCause.RegistryNotFound())));
    }

    @Test
    void listsTheMismatchedFields() {
        assertEquals(
                "Lead 1020304050 rejected: national registry data does not match on birthDate, lastName.",
                Cli.render(
                        ID,
                        new Decision.Rejected(new RejectionCause.RegistryMismatch(List.of("birthDate", "lastName")))));
    }

    @Test
    void namesTheNumberAndTheMinimumOnTheScoreRejectionLine() {
        assertEquals(
                "Lead 1020304050 rejected: qualification score 57 is not above 60.",
                Cli.render(ID, new Decision.Rejected(new RejectionCause.ScoreTooLow(57))));
    }

    @Test
    void namesTheFraudCheckOnItsRejectionLine() {
        assertEquals(
                "Lead 1040506070 rejected: flagged by the fraud check.",
                Cli.render(new NationalId("1040506070"), new Decision.Rejected(new RejectionCause.FraudDetected())));
    }

    @Test
    void namesTheCaseOnTheAnalystRejectionLine() {
        assertEquals(
                "Lead 1020304050 rejected: manual review case 1020304050-17553 was rejected by an analyst.",
                Cli.render(ID, new Decision.Rejected(new RejectionCause.ReviewRejected(new CaseId("1020304050-17553")))));
    }

    @Test
    void namesTheCaseOnTheLineThatOpensIt() {
        assertEquals("Case 1020304050-17553 opened.", Cli.opened(new CaseId("1020304050-17553")));
    }

    @Test
    void namesTheStepAndTheReasonOnThePendingLine() {
        assertEquals(
                "Lead 1020304050 pending manual review at step BUREAU: connection refused.",
                Cli.render(
                        ID, new Decision.PendingManualReview(new Checkpoint(LEAD, Step.BUREAU, "connection refused"))));
    }

    @Test
    void rendersALineForEveryDecisionAndEveryRejectionCause() {
        var decisions = List.<Decision>of(
                new Decision.Converted(new Prospect(LEAD, 75)),
                new Decision.PendingManualReview(new Checkpoint(LEAD, Step.SCORE, "timed out")),
                new Decision.Rejected(new RejectionCause.LeadNotInDatabase(ID)),
                new Decision.Rejected(new RejectionCause.RegistryNotFound()),
                new Decision.Rejected(new RejectionCause.RegistryMismatch(List.of("firstName"))),
                new Decision.Rejected(new RejectionCause.JudicialRecords(2)),
                new Decision.Rejected(new RejectionCause.Sanctioned("OFAC")),
                new Decision.Rejected(new RejectionCause.FraudDetected()),
                new Decision.Rejected(new RejectionCause.ScoreTooLow(12)),
                new Decision.Rejected(new RejectionCause.ReviewRejected(new CaseId("1020304050-17553"))));

        for (var decision : decisions) {
            var line = Cli.render(ID, decision);
            assertFalse(line.isBlank(), decision.toString());
            assertEquals(1, line.lines().count(), line);
        }
    }
}
