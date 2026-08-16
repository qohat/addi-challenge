package com.addi.lead.domain;

/**
 * A lead plus the score that converted it. The difference is evidence, not a status field, and it is
 * constructed at exactly one site: the conversion arm of {@link Decisions#terminal}.
 */
public record Prospect(Lead lead, int score) {}
