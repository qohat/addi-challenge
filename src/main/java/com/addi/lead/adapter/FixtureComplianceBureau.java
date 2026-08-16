package com.addi.lead.adapter;

import com.addi.lead.domain.BureauOutcome;
import com.addi.lead.domain.Lead;
import com.addi.lead.port.ComplianceBureau;
import com.addi.lead.port.Latency;
import java.nio.file.Path;
import java.time.Duration;

/** Reads {@code bureau.csv} on every call. The cache in front of it is spec 04. */
public record FixtureComplianceBureau(Path fixtures, Latency latency) implements ComplianceBureau {

    @Override
    public BureauOutcome screen(Lead lead) {
        try {
            var row = Fixtures.row(fixtures, "bureau.csv", lead.id());
            if (row.isEmpty()) {
                return new BureauOutcome.Clear();
            }
            var cells = row.get();
            latency.pause(Duration.ofMillis(Long.parseLong(cells[2].trim())));
            if (Fixtures.isDown(cells[3])) {
                return new BureauOutcome.Unavailable("the compliance bureau is down");
            }
            var list = cells[1].trim();
            return list.isEmpty() ? new BureauOutcome.Clear() : new BureauOutcome.Sanctioned(list);
        } catch (Exception e) {
            return new BureauOutcome.Unavailable(Fixtures.reason(e));
        }
    }
}
