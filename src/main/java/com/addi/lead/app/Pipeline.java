package com.addi.lead.app;

import com.addi.lead.domain.Decision;
import com.addi.lead.domain.Decisions;
import com.addi.lead.domain.JudicialOutcome;
import com.addi.lead.domain.Lead;
import com.addi.lead.domain.NationalId;
import com.addi.lead.domain.RegistryOutcome;
import com.addi.lead.domain.RejectionCause;
import com.addi.lead.domain.Step;
import com.addi.lead.domain.StepOutcome;
import com.addi.lead.port.ComplianceBureau;
import com.addi.lead.port.JudicialRecords;
import com.addi.lead.port.LeadRepository;
import com.addi.lead.port.NationalRegistry;
import com.addi.lead.port.QualificationScore;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * Sequences the four validations and stops at the first outcome that decides anything. Registry and
 * judicial are forked together; every other step waits for the one before it, because the brief says
 * so. Concurrency lives here and never in the domain.
 */
public final class Pipeline {

    private final Config config;
    private final LeadRepository leads;
    private final NationalRegistry registry;
    private final JudicialRecords judicial;
    private final ComplianceBureau bureau;
    private final QualificationScore score;

    public Pipeline(
            Config config,
            LeadRepository leads,
            NationalRegistry registry,
            JudicialRecords judicial,
            ComplianceBureau bureau,
            QualificationScore score) {
        this.config = config;
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
        if (from != Step.REGISTRY) {
            return sequential(lead, from);
        }
        return firstDecision(lead, fanOut(lead)).orElseGet(() -> sequential(lead, Step.BUREAU));
    }

    /**
     * The one fork-join point in the system. {@code awaitAll} is the joiner because the adapters
     * return values and never throw, so a joiner that unwraps exceptions would describe a failure
     * mode this design has removed.
     */
    private List<StepOutcome> fanOut(Lead lead) {
        try (var scope = StructuredTaskScope.<StepOutcome, Void>open(
                Joiner.awaitAll(), configuration -> configuration.withTimeout(config.parallelTimeout()))) {
            var registryTask = scope.fork(() -> registry.check(lead));
            var judicialTask = scope.fork(() -> judicial.check(lead));
            scope.join();
            return List.of(
                    completed(registryTask, new RegistryOutcome.Unavailable(timedOut())),
                    completed(judicialTask, new JudicialOutcome.Unavailable(timedOut())));
        } catch (StructuredTaskScope.TimeoutException e) {
            // Both branches, even one that finished: the bureau needs both, so half an answer is none.
            return unavailable(timedOut());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable("interrupted");
        }
    }

    /** A subtask that is not {@code SUCCESS} was cancelled, which is the timeout under another name. */
    private static StepOutcome completed(Subtask<? extends StepOutcome> task, StepOutcome cancelled) {
        return task.state() == Subtask.State.SUCCESS ? task.get() : cancelled;
    }

    private List<StepOutcome> unavailable(String reason) {
        return List.of(new RegistryOutcome.Unavailable(reason), new JudicialOutcome.Unavailable(reason));
    }

    private String timedOut() {
        return "timeout after " + config.parallelTimeout().toMillis() + "ms";
    }

    /** Canonical order, so which branch finished first cannot change the decision (ADR 0002). */
    private static Optional<Decision> firstDecision(Lead lead, List<StepOutcome> outcomes) {
        return outcomes.stream()
                .map(outcome -> Decisions.terminal(lead, outcome))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Decision sequential(Lead lead, Step from) {
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
