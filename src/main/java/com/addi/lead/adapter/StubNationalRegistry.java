package com.addi.lead.adapter;

import com.addi.lead.domain.Lead;
import com.addi.lead.domain.RegistryOutcome;
import com.addi.lead.port.NationalRegistry;

/** Fixed clean outcome, so spec 02 has something to wire. Deleted in spec 03. */
public record StubNationalRegistry() implements NationalRegistry {

    @Override
    public RegistryOutcome check(Lead lead) {
        return new RegistryOutcome.Matched();
    }
}
