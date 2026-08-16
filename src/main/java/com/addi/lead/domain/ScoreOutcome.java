package com.addi.lead.domain;

/**
 * Carries the number, not the verdict. The threshold rule lives at the single decision site where a
 * reviewer will look for it, not in the adapter that rolled the dice.
 */
public sealed interface ScoreOutcome extends StepOutcome {

    record Scored(int value) implements ScoreOutcome {}

    record Unavailable(String reason) implements ScoreOutcome {}
}
