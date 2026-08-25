package com.addi.lead.domain;

/**
 * Carries the answer the fraud service gave, not the verdict. Whether a flagged lead is rejected is
 * decided at the single collapse point, the way {@link ScoreOutcome} leaves the threshold to it.
 */
public sealed interface FraudOutcome extends StepOutcome {

    record Assessed(boolean fraudulent) implements FraudOutcome {}

    record Unavailable(String reason) implements FraudOutcome {}
}
