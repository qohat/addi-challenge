package com.addi.lead.port;

import com.addi.lead.domain.Lead;
import com.addi.lead.domain.ScoreOutcome;

/** Randomness is an effect, so it enters here and takes a fixed value in tests. */
public interface QualificationScore {
    ScoreOutcome score(Lead lead);
}
