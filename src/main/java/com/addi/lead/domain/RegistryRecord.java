package com.addi.lead.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What the external registry holds on a person. The adapter reads it; the comparison below is the
 * rule, and it is pure, so the interesting part of spec 03 is tested without touching a file.
 *
 * <p>Email is not compared: a civil registry does not hold one (`docs/assumptions.md`).
 */
public record RegistryRecord(NationalId id, String firstName, String lastName, LocalDate birthDate) {

    /** Differing fields come back in declaration order, so the rejection line reads the same way twice. */
    public RegistryOutcome compare(Lead lead) {
        var differing = new ArrayList<String>();
        if (!sameName(firstName, lead.firstName())) {
            differing.add("firstName");
        }
        if (!sameName(lastName, lead.lastName())) {
            differing.add("lastName");
        }
        if (!birthDate.equals(lead.birthDate())) {
            differing.add("birthDate");
        }
        return differing.isEmpty() ? new RegistryOutcome.Matched() : new RegistryOutcome.Mismatch(differing);
    }

    /** Two registries spelling a name differently is not two people. */
    private static boolean sameName(String one, String other) {
        return one.trim().equalsIgnoreCase(other.trim());
    }
}
