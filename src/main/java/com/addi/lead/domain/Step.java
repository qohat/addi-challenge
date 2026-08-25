package com.addi.lead.domain;

/**
 * Declaration order is the canonical order. Registry and judicial execute in parallel from spec 03,
 * but their outcomes are consumed in this order, which is what makes "the first unresolved step"
 * well defined for a checkpoint.
 */
public enum Step {
    REGISTRY,
    JUDICIAL,
    BUREAU,
    FRAUD,
    SCORE
}
