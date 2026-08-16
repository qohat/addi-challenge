package com.addi.lead.domain;

/**
 * Where a run stopped and why. The steps before {@code pending} resolved cleanly or the run would
 * have ended there, so storing them again would be a second copy free to disagree with this one.
 */
public record Checkpoint(Lead lead, Step pending, String reason) {}
