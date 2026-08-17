package com.addi.lead.adapter;

import com.addi.lead.domain.Email;
import com.addi.lead.domain.Lead;
import com.addi.lead.domain.NationalId;
import com.addi.lead.port.LeadRepository;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The local CRM database. No external database, so the map is the database. */
public record InMemoryLeadRepository(Map<NationalId, Lead> leads) implements LeadRepository {

    public InMemoryLeadRepository {
        leads = Map.copyOf(leads);
    }

    /**
     * Seeded from {@code leads.csv}, once, at wiring time. An unreadable seed leaves the database
     * empty rather than throwing: {@link LeadRepository} has no unavailable case, and a lead that is
     * not in the database is already a decision the CLI can print.
     */
    public static InMemoryLeadRepository fromFixtures(Path fixtures) {
        var leads = new HashMap<NationalId, Lead>();
        try {
            for (var cells : Fixtures.rows(fixtures, "leads.csv")) {
                var id = new NationalId(cells[0].trim());
                leads.put(
                        id,
                        new Lead(id, cells[1], cells[2], LocalDate.parse(cells[3].trim()), new Email(cells[4].trim())));
            }
        } catch (Exception e) {
            return new InMemoryLeadRepository(Map.of());
        }
        return new InMemoryLeadRepository(leads);
    }

    @Override
    public Optional<Lead> findById(NationalId id) {
        return Optional.ofNullable(leads.get(id));
    }
}
