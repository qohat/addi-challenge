package com.addi.lead.port;

import com.addi.lead.domain.BureauOutcome;
import com.addi.lead.domain.Lead;

/** The cache in front of it is spec 04, and it lives behind this same port. */
public interface ComplianceBureau {
    BureauOutcome screen(Lead lead);
}
