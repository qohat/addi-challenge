package com.addi.lead.domain;

/** What the national judicial archives said about a lead. Any count of one or more rejects. */
public sealed interface JudicialOutcome extends StepOutcome {

    record Clear() implements JudicialOutcome {}

    record RecordsFound(int count) implements JudicialOutcome {}

    record Unavailable(String reason) implements JudicialOutcome {}
}
