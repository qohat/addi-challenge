package com.addi.lead.port;

import com.addi.lead.domain.JudicialOutcome;
import com.addi.lead.domain.Lead;

/** Handed the whole lead although it only needs the id, so all four ports have one signature. */
public interface JudicialRecords {
    JudicialOutcome check(Lead lead);
}
