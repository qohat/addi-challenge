package com.addi.lead.adapter;

import com.addi.lead.domain.Lead;
import com.addi.lead.domain.NationalId;
import com.addi.lead.domain.RegistryOutcome;
import com.addi.lead.domain.RegistryRecord;
import com.addi.lead.port.Latency;
import com.addi.lead.port.NationalRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

/**
 * Reads {@code registry.csv} and hands the record to the domain. The adapter decides nothing about
 * whether the person matches; it only decides whether it got an answer at all.
 */
public record FixtureNationalRegistry(Path fixtures, Latency latency) implements NationalRegistry {

    @Override
    public RegistryOutcome check(Lead lead) {
        try {
            var row = Fixtures.row(fixtures, "registry.csv", lead.id());
            if (row.isEmpty()) {
                return new RegistryOutcome.NotFound();
            }
            var cells = row.get();
            latency.pause(Duration.ofMillis(Long.parseLong(cells[4].trim())));
            if (Fixtures.isDown(cells[5])) {
                return new RegistryOutcome.Unavailable("the national registry is down");
            }
            return new RegistryRecord(
                            new NationalId(cells[0].trim()), cells[1], cells[2], LocalDate.parse(cells[3].trim()))
                    .compare(lead);
        } catch (Exception e) {
            return new RegistryOutcome.Unavailable(Fixtures.reason(e));
        }
    }
}
