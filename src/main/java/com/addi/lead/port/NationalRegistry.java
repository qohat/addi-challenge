package com.addi.lead.port;

import com.addi.lead.domain.Lead;
import com.addi.lead.domain.RegistryOutcome;

/** Synchronous and blocking. The fan-out belongs to the orchestrator, never to a port. */
public interface NationalRegistry {
    RegistryOutcome check(Lead lead);
}
