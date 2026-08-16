package com.addi.lead.domain;

import java.time.Instant;

/** A checkpoint on disk, awaiting an analyst. The queue and the resume are spec 06. */
public record ReviewCase(CaseId id, Instant openedAt, Checkpoint checkpoint, CaseStatus status) {}
