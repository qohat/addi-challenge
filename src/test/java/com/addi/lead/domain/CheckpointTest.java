package com.addi.lead.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.addi.lead.FixtureDir;
import org.junit.jupiter.api.Test;

/** The resume rule, which is the whole of what approval means. */
class CheckpointTest {

    @Test
    void approvalResumesAtTheStepAfterThePendingOne() {
        assertEquals(Step.JUDICIAL, resumeFrom(Step.REGISTRY));
        assertEquals(Step.BUREAU, resumeFrom(Step.JUDICIAL));
        assertEquals(Step.SCORE, resumeFrom(Step.BUREAU));
    }

    @Test
    void anApprovedScoreIsRerunBecauseAnAnalystCannotAttestANumber() {
        assertEquals(Step.SCORE, resumeFrom(Step.SCORE));
    }

    private static Step resumeFrom(Step pending) {
        return new Checkpoint(FixtureDir.LEAD, pending, "unavailable").resumeFrom();
    }
}
