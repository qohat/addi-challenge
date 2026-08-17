package com.addi.lead.adapter;

import com.addi.lead.domain.Lead;
import com.addi.lead.domain.ScoreOutcome;
import com.addi.lead.port.Latency;
import com.addi.lead.port.QualificationScore;
import com.addi.lead.port.Randomness;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Reads {@code score.csv} for latency and availability only. The number itself is never fixtured: the
 * brief says random, so it comes from the randomness port on every call (ADR 0006).
 */
public record FixtureQualificationScore(Path fixtures, Latency latency, Randomness randomness)
        implements QualificationScore {

    @Override
    public ScoreOutcome score(Lead lead) {
        try {
            var row = Fixtures.row(fixtures, "score.csv", lead.id());
            if (row.isPresent()) {
                var cells = row.get();
                latency.pause(Duration.ofMillis(Long.parseLong(cells[1].trim())));
                if (Fixtures.isDown(cells[2])) {
                    return new ScoreOutcome.Unavailable("the scoring service is down");
                }
            }
            return new ScoreOutcome.Scored(randomness.nextInt(101));
        } catch (Exception e) {
            return new ScoreOutcome.Unavailable(Fixtures.reason(e));
        }
    }
}
