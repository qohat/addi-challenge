package com.addi.lead.port;

import com.addi.lead.domain.Lead;
import com.addi.lead.domain.NationalId;
import java.util.Optional;

/** The local CRM database. Not one of the four validations, which is why a miss is not a step. */
public interface LeadRepository {
    Optional<Lead> findById(NationalId id);
}
