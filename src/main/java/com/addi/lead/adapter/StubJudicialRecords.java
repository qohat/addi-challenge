package com.addi.lead.adapter;

import com.addi.lead.domain.JudicialOutcome;
import com.addi.lead.domain.Lead;
import com.addi.lead.port.JudicialRecords;

/** Fixed clean outcome, so spec 02 has something to wire. Deleted in spec 03. */
public record StubJudicialRecords() implements JudicialRecords {

    @Override
    public JudicialOutcome check(Lead lead) {
        return new JudicialOutcome.Clear();
    }
}
