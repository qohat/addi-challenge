package com.addi.lead.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * One assertion per case of every step outcome. This switch is the map of the system, so every arm
 * of it is pinned here rather than sampled.
 */
class DecisionsTest {

    private static final Lead LEAD = new Lead(
            new NationalId("1020304050"),
            "Ana",
            "Restrepo",
            LocalDate.of(1990, 3, 14),
            new Email("ana.restrepo@example.com"));

    private static Optional<Decision> terminal(StepOutcome outcome) {
        return Decisions.terminal(LEAD, outcome);
    }

    private static Optional<Decision> rejected(RejectionCause cause) {
        return Optional.of(new Decision.Rejected(cause));
    }

    private static Optional<Decision> review(Step step, String reason) {
        return Optional.of(new Decision.PendingManualReview(new Checkpoint(LEAD, step, reason)));
    }

    @Test
    void cleanOutcomesContinue() {
        assertEquals(Optional.empty(), terminal(new RegistryOutcome.Matched()));
        assertEquals(Optional.empty(), terminal(new JudicialOutcome.Clear()));
        assertEquals(Optional.empty(), terminal(new BureauOutcome.Clear()));
    }

    @Test
    void registryNotFoundRejects() {
        assertEquals(rejected(new RejectionCause.RegistryNotFound()), terminal(new RegistryOutcome.NotFound()));
    }

    @Test
    void registryMismatchRejectsAndKeepsTheFields() {
        var fields = List.of("birthDate", "lastName");

        assertEquals(
                rejected(new RejectionCause.RegistryMismatch(fields)),
                terminal(new RegistryOutcome.Mismatch(fields)));
    }

    @Test
    void notFoundAndMismatchAreDifferentCauses() {
        assertNotEquals(
                terminal(new RegistryOutcome.NotFound()),
                terminal(new RegistryOutcome.Mismatch(List.of("firstName"))));
    }

    @Test
    void judicialRecordsRejectWithTheirCount() {
        assertEquals(rejected(new RejectionCause.JudicialRecords(3)), terminal(new JudicialOutcome.RecordsFound(3)));
    }

    @Test
    void sanctionsRejectWithTheirList() {
        assertEquals(rejected(new RejectionCause.Sanctioned("OFAC")), terminal(new BureauOutcome.Sanctioned("OFAC")));
    }

    @Test
    void aFraudFlagRejects() {
        assertEquals(rejected(new RejectionCause.FraudDetected()), terminal(new FraudOutcome.Assessed(true)));
    }

    @Test
    void aCleanFraudCheckContinues() {
        assertEquals(Optional.empty(), terminal(new FraudOutcome.Assessed(false)));
    }

    @Test
    void aScoreAboveTheMinimumConverts() {
        for (var value : List.of(61, 75)) {
            assertEquals(
                    Optional.of(new Decision.Converted(new Prospect(LEAD, value))),
                    terminal(new ScoreOutcome.Scored(value)),
                    String.valueOf(value));
        }
    }

    @Test
    void exactlySixtyRejects() {
        assertEquals(rejected(new RejectionCause.ScoreTooLow(60)), terminal(new ScoreOutcome.Scored(60)));
    }

    @Test
    void aLowScoreRejectsWithItsValue() {
        var decision = terminal(new ScoreOutcome.Scored(0));

        assertEquals(rejected(new RejectionCause.ScoreTooLow(0)), decision);
        assertNotEquals(decision, terminal(new ScoreOutcome.Scored(60)));
    }

    /** Five arms, one per step, and nowhere else in the system. */
    @Test
    void everyUnavailableDependencyRoutesToItsOwnStep() {
        assertEquals(review(Step.REGISTRY, "registry timeout"), terminal(new RegistryOutcome.Unavailable("registry timeout")));
        assertEquals(review(Step.JUDICIAL, "judicial timeout"), terminal(new JudicialOutcome.Unavailable("judicial timeout")));
        assertEquals(review(Step.BUREAU, "bureau 503"), terminal(new BureauOutcome.Unavailable("bureau 503")));
        assertEquals(review(Step.FRAUD, "fraud service 502"), terminal(new FraudOutcome.Unavailable("fraud service 502")));
        assertEquals(review(Step.SCORE, "scoring offline"), terminal(new ScoreOutcome.Unavailable("scoring offline")));
    }
}
