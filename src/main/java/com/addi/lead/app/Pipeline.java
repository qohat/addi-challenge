package com.addi.lead.app;

import com.addi.lead.domain.Decision;
import com.addi.lead.domain.Decisions;
import com.addi.lead.domain.Lead;
import com.addi.lead.domain.NationalId;
import com.addi.lead.domain.RejectionCause;
import com.addi.lead.domain.Step;
import com.addi.lead.domain.StepOutcome;
import com.addi.lead.port.ComplianceBureau;
import com.addi.lead.port.JudicialRecords;
import com.addi.lead.port.LeadRepository;
import com.addi.lead.port.NationalRegistry;
import com.addi.lead.port.QualificationScore;

/**
 * Sequences the four validations and stops at the first outcome that decides anything. Spec 03
 * forks registry and judicial inside {@link #run}; the decisions do not change, because their
 * outcomes are consumed in canonical order either way.
 */
public final class Pipeline {

    private final LeadRepository leads;
    private final NationalRegistry registry;
    private final JudicialRecords judicial;
    private final ComplianceBureau bureau;
    private final QualificationScore score;

    public Pipeline(
            LeadRepository leads,
            NationalRegistry registry,
            JudicialRecords judicial,
            ComplianceBureau bureau,
            QualificationScore score) {
        this.leads = leads;
        this.registry = registry;
        this.judicial = judicial;
        this.bureau = bureau;
        this.score = score;
    }

    /** The only producer of {@code LeadNotInDatabase}: the lookup is not a validation step. */
    public Decision validate(NationalId id) {
        return leads.findById(id)
                .map(lead -> run(lead, Step.REGISTRY))
                .orElseGet(() -> new Decision.Rejected(new RejectionCause.LeadNotInDatabase(id)));
    }

    /** A fresh run passes {@code REGISTRY}; a resume passes the checkpoint's pending step. */
    public Decision run(Lead lead, Step from) {
        for (var step : Step.values()) {
            if (step.ordinal() < from.ordinal()) {
                continue;
            }
            var decision = Decisions.terminal(lead, outcomeOf(step, lead));
            if (decision.isPresent()) {
                return decision.get();
            }
        }
        // The score step always decides, so falling out is an invariant violation, not an outcome.
        throw new IllegalStateException("Every step ran clean without deciding: " + lead.id().value());
    }

    private StepOutcome outcomeOf(Step step, Lead lead) {
        return switch (step) {
            case REGISTRY -> registry.check(lead);
            case JUDICIAL -> judicial.check(lead);
            case BUREAU -> bureau.screen(lead);
            case SCORE -> score.score(lead);
        };
    }
}
