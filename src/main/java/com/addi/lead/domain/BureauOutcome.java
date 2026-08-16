package com.addi.lead.domain;

/**
 * What the compliance bureau said about a lead. Whether the answer came from the cache is
 * deliberately not a case here: it is not a different decision.
 */
public sealed interface BureauOutcome extends StepOutcome {

    record Clear() implements BureauOutcome {}

    record Sanctioned(String list) implements BureauOutcome {}

    record Unavailable(String reason) implements BureauOutcome {}
}
