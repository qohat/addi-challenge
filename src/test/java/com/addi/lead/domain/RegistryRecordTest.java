package com.addi.lead.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The interesting rule in spec 03, and it never touches a file. */
class RegistryRecordTest {

    private static final Lead LEAD = new Lead(
            new NationalId("1020304050"),
            "Ana",
            "Restrepo",
            LocalDate.of(1990, 3, 14),
            new Email("ana.restrepo@example.com"));

    private static RegistryOutcome compare(String firstName, String lastName, LocalDate birthDate) {
        return new RegistryRecord(LEAD.id(), firstName, lastName, birthDate).compare(LEAD);
    }

    @Test
    void anIdenticalRecordMatches() {
        assertEquals(new RegistryOutcome.Matched(), compare("Ana", "Restrepo", LocalDate.of(1990, 3, 14)));
    }

    @Test
    void aDifferentBirthDateIsTheOnlyFieldListed() {
        assertEquals(
                new RegistryOutcome.Mismatch(List.of("birthDate")),
                compare("Ana", "Restrepo", LocalDate.of(1990, 3, 15)));
    }

    @Test
    void twoDifferingFieldsAreListedInFieldOrder() {
        assertEquals(
                new RegistryOutcome.Mismatch(List.of("firstName", "birthDate")),
                compare("Andrea", "Restrepo", LocalDate.of(1991, 3, 14)));
    }

    @Test
    void bothNamesCanDifferAtOnce() {
        assertEquals(
                new RegistryOutcome.Mismatch(List.of("firstName", "lastName")),
                compare("Andrea", "Restrepa", LocalDate.of(1990, 3, 14)));
    }

    @Test
    void letterCaseAndSurroundingSpacesDoNotMatter() {
        assertEquals(new RegistryOutcome.Matched(), compare("  ana ", "RESTREPO  ", LocalDate.of(1990, 3, 14)));
    }
}
