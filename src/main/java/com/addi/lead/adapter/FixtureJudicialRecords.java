package com.addi.lead.adapter;

import com.addi.lead.domain.JudicialOutcome;
import com.addi.lead.domain.Lead;
import com.addi.lead.port.JudicialRecords;
import com.addi.lead.port.Latency;
import java.nio.file.Path;
import java.time.Duration;

/** Reads {@code judicial.csv}. No row means the archives hold nothing on this person. */
public record FixtureJudicialRecords(Path fixtures, Latency latency) implements JudicialRecords {

    @Override
    public JudicialOutcome check(Lead lead) {
        try {
            var row = Fixtures.row(fixtures, "judicial.csv", lead.id());
            if (row.isEmpty()) {
                return new JudicialOutcome.Clear();
            }
            var cells = row.get();
            latency.pause(Duration.ofMillis(Long.parseLong(cells[2].trim())));
            if (Fixtures.isDown(cells[3])) {
                return new JudicialOutcome.Unavailable("the judicial archives are down");
            }
            var count = Integer.parseInt(cells[1].trim());
            return count == 0 ? new JudicialOutcome.Clear() : new JudicialOutcome.RecordsFound(count);
        } catch (Exception e) {
            return new JudicialOutcome.Unavailable(Fixtures.reason(e));
        }
    }
}
