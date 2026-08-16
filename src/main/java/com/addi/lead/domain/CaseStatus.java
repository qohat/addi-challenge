package com.addi.lead.domain;

/** A resolved case is terminal, and that has to be enforceable on a file still on disk. */
public enum CaseStatus {
    OPEN,
    APPROVED,
    REJECTED
}
