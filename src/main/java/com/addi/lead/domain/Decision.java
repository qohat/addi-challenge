package com.addi.lead.domain;

/**
 * Three cases, three exit codes: 0, 1, 2. Exit code 3 is absent on purpose — an unparseable command
 * line never reaches the domain, so an input error is not a decision about a lead.
 */
public sealed interface Decision {

    record Converted(Prospect prospect) implements Decision {}

    record Rejected(RejectionCause cause) implements Decision {}

    record PendingManualReview(Checkpoint checkpoint) implements Decision {}
}
