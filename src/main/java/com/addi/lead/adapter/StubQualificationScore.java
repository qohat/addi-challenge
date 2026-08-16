package com.addi.lead.adapter;

import com.addi.lead.domain.Lead;
import com.addi.lead.domain.ScoreOutcome;
import com.addi.lead.port.QualificationScore;

/** Fixed score, so spec 02 has something to wire. Randomness and the threshold come later. */
public record StubQualificationScore() implements QualificationScore {

    @Override
    public ScoreOutcome score(Lead lead) {
        return new ScoreOutcome.Scored(75);
    }
}
