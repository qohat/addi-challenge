package com.addi.lead.domain;

import java.util.List;

/**
 * Why a lead did not become a prospect. Spec 05 adds the score threshold and spec 06 adds a
 * reviewer rejection; each addition breaks every exhaustive match over this type, which is the
 * point of it.
 */
public sealed interface RejectionCause {

    /** Not a step outcome: the lookup is not a validation, so its miss never enters the switch. */
    record LeadNotInDatabase(NationalId id) implements RejectionCause {}

    record RegistryNotFound() implements RejectionCause {}

    record RegistryMismatch(List<String> fields) implements RejectionCause {
        public RegistryMismatch {
            fields = List.copyOf(fields);
        }
    }

    record JudicialRecords(int count) implements RejectionCause {}

    record Sanctioned(String list) implements RejectionCause {}

    /** No component: the fraud service answers a boolean, so nothing could differ between two of these. */
    record FraudDetected() implements RejectionCause {}

    record ScoreTooLow(int value) implements RejectionCause {}

    /** The only cause no step outcome produces: an analyst decided it, so {@link Decisions} never builds it. */
    record ReviewRejected(CaseId caseId) implements RejectionCause {}
}
