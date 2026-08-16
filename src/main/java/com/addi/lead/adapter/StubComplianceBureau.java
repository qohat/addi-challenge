package com.addi.lead.adapter;

import com.addi.lead.domain.BureauOutcome;
import com.addi.lead.domain.Lead;
import com.addi.lead.port.ComplianceBureau;

/** Fixed clean outcome, so spec 02 has something to wire. Deleted in spec 03. */
public record StubComplianceBureau() implements ComplianceBureau {

    @Override
    public BureauOutcome screen(Lead lead) {
        return new BureauOutcome.Clear();
    }
}
