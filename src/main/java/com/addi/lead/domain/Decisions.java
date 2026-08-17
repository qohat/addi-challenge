package com.addi.lead.domain;

import java.util.Optional;

/**
 * The one place a step outcome becomes a terminal decision. No {@code default}: a new failure mode
 * breaks this switch and nothing else, and the build stays red until it lands here.
 *
 * <p>An empty result means the step was clean and the pipeline runs the next one. That emptiness is
 * the only "continue" signal in the system, so no pending case pollutes {@link Decision}.
 */
public final class Decisions {

    public static final int MINIMUM_SCORE = 60;

    public static Optional<Decision> terminal(Lead lead, StepOutcome outcome) {
        return switch (outcome) {
            case RegistryOutcome.Matched ignored -> Optional.empty();
            case RegistryOutcome.NotFound ignored -> rejected(new RejectionCause.RegistryNotFound());
            case RegistryOutcome.Mismatch(var fields) -> rejected(new RejectionCause.RegistryMismatch(fields));
            case RegistryOutcome.Unavailable(var why) -> review(lead, Step.REGISTRY, why);

            case JudicialOutcome.Clear ignored -> Optional.empty();
            case JudicialOutcome.RecordsFound(var count) -> rejected(new RejectionCause.JudicialRecords(count));
            case JudicialOutcome.Unavailable(var why) -> review(lead, Step.JUDICIAL, why);

            case BureauOutcome.Clear ignored -> Optional.empty();
            case BureauOutcome.Sanctioned(var list) -> rejected(new RejectionCause.Sanctioned(list));
            case BureauOutcome.Unavailable(var why) -> review(lead, Step.BUREAU, why);

            case ScoreOutcome.Scored(var value) ->
                    value > MINIMUM_SCORE ? converted(lead, value) : rejected(new RejectionCause.ScoreTooLow(value));
            case ScoreOutcome.Unavailable(var why) -> review(lead, Step.SCORE, why);
        };
    }

    private static Optional<Decision> rejected(RejectionCause cause) {
        return Optional.of(new Decision.Rejected(cause));
    }

    private static Optional<Decision> review(Lead lead, Step step, String reason) {
        return Optional.of(new Decision.PendingManualReview(new Checkpoint(lead, step, reason)));
    }

    private static Optional<Decision> converted(Lead lead, int score) {
        return Optional.of(new Decision.Converted(new Prospect(lead, score)));
    }

    private Decisions() {}
}
