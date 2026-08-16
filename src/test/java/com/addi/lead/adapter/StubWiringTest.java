package com.addi.lead.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.addi.lead.app.Pipeline;
import com.addi.lead.domain.Decision;
import com.addi.lead.domain.NationalId;
import org.junit.jupiter.api.Test;

/** What spec 02 will wire in the composition root, wired here instead. The stubs go in spec 03. */
class StubWiringTest {

    private static final Pipeline PIPELINE = new Pipeline(
            InMemoryLeadRepository.seeded(),
            new StubNationalRegistry(),
            new StubJudicialRecords(),
            new StubComplianceBureau(),
            new StubQualificationScore());

    @Test
    void aSeededLeadConverts() {
        var decision = PIPELINE.validate(new NationalId("1020304050"));

        var converted = assertInstanceOf(Decision.Converted.class, decision);
        assertEquals("1020304050", converted.prospect().lead().id().value());
        assertEquals(75, converted.prospect().score());
    }

    @Test
    void anUnseededLeadIsRejected() {
        assertInstanceOf(Decision.Rejected.class, PIPELINE.validate(new NationalId("9999999999")));
    }
}
