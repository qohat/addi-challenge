package com.addi.lead.adapter;

import com.addi.lead.domain.FraudOutcome;
import com.addi.lead.domain.Lead;
import com.addi.lead.port.FraudCheck;
import com.addi.lead.port.Latency;
import java.nio.file.Path;
import java.time.Duration;

/** Reads {@code fraud.csv}. No row means the service has nothing on this person, which is not fraud. */
public record FixtureFraudCheck(Path fixtures, Latency latency) implements FraudCheck {

    @Override
    public FraudOutcome check(Lead lead) {
        try {
            var row = Fixtures.row(fixtures, "fraud.csv", lead.id());
            if (row.isEmpty()) {
                return new FraudOutcome.Assessed(false);
            }
            var cells = row.get();
            latency.pause(Duration.ofMillis(Long.parseLong(cells[2].trim())));
            if (Fixtures.isDown(cells[3])) {
                return new FraudOutcome.Unavailable("the fraud service is down");
            }
            return new FraudOutcome.Assessed(Fixtures.flag(cells[1]));
        } catch (Exception e) {
            return new FraudOutcome.Unavailable(Fixtures.reason(e));
        }
    }
}
