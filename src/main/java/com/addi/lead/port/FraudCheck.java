package com.addi.lead.port;

import com.addi.lead.domain.FraudOutcome;
import com.addi.lead.domain.Lead;

/** The fifth validation, between the bureau and the score. It answers a boolean or nothing at all. */
public interface FraudCheck {
    FraudOutcome check(Lead lead);
}
