package com.addi.lead.domain;

/**
 * Where a run stopped and why. The steps before {@code pending} resolved cleanly or the run would
 * have ended there, so storing them again would be a second copy free to disagree with this one.
 */
public record Checkpoint(Lead lead, Step pending, String reason) {

    /**
     * Approving takes the pending step as clean, so the run continues past it. The score is the
     * exception: an analyst cannot attest a number, so approving one re-runs the port (ADR 0004).
     */
    public Step resumeFrom() {
        return switch (pending) {
            case REGISTRY -> Step.JUDICIAL;
            case JUDICIAL -> Step.BUREAU;
            case BUREAU -> Step.SCORE;
            case SCORE -> Step.SCORE;
        };
    }
}
