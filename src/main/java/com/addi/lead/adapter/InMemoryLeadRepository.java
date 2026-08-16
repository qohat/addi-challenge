package com.addi.lead.adapter;

import com.addi.lead.domain.Email;
import com.addi.lead.domain.Lead;
import com.addi.lead.domain.NationalId;
import com.addi.lead.port.LeadRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/** The local CRM database. No external database, so the map is the database. */
public record InMemoryLeadRepository(Map<NationalId, Lead> leads) implements LeadRepository {

    public InMemoryLeadRepository {
        leads = Map.copyOf(leads);
    }

    /** Three hand-written leads. Spec 03 replaces the seed with fixtures keyed by national id. */
    public static InMemoryLeadRepository seeded() {
        return new InMemoryLeadRepository(Map.of(
                new NationalId("1020304050"),
                        new Lead(
                                new NationalId("1020304050"),
                                "Ana",
                                "Restrepo",
                                LocalDate.of(1990, 3, 14),
                                new Email("ana.restrepo@example.com")),
                new NationalId("1030405060"),
                        new Lead(
                                new NationalId("1030405060"),
                                "Carlos",
                                "Mejia",
                                LocalDate.of(1985, 11, 2),
                                new Email("carlos.mejia@example.com")),
                new NationalId("1040506070"),
                        new Lead(
                                new NationalId("1040506070"),
                                "Lucia",
                                "Gomez",
                                LocalDate.of(1997, 6, 25),
                                new Email("lucia.gomez@example.com"))));
    }

    @Override
    public Optional<Lead> findById(NationalId id) {
        return Optional.ofNullable(leads.get(id));
    }
}
