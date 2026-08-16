package com.addi.lead.domain;

import java.util.List;

/** What the external national registry said about a lead. */
public sealed interface RegistryOutcome extends StepOutcome {

    record Matched() implements RegistryOutcome {}

    record NotFound() implements RegistryOutcome {}

    record Mismatch(List<String> fields) implements RegistryOutcome {
        public Mismatch {
            fields = List.copyOf(fields);
        }
    }

    record Unavailable(String reason) implements RegistryOutcome {}
}
