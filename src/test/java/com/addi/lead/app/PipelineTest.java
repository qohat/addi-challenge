package com.addi.lead.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.addi.lead.domain.BureauOutcome;
import com.addi.lead.domain.Checkpoint;
import com.addi.lead.domain.Decision;
import com.addi.lead.domain.Email;
import com.addi.lead.domain.JudicialOutcome;
import com.addi.lead.domain.Lead;
import com.addi.lead.domain.NationalId;
import com.addi.lead.domain.Prospect;
import com.addi.lead.domain.RegistryOutcome;
import com.addi.lead.domain.RejectionCause;
import com.addi.lead.domain.ScoreOutcome;
import com.addi.lead.domain.Step;
import com.addi.lead.port.ComplianceBureau;
import com.addi.lead.port.JudicialRecords;
import com.addi.lead.port.LeadRepository;
import com.addi.lead.port.NationalRegistry;
import com.addi.lead.port.QualificationScore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Fakes rather than the stubs, so every arm is reachable independently and the sequencing is
 * asserted by what was called, not only by what came back.
 */
class PipelineTest {

    private static final Lead LEAD = new Lead(
            new NationalId("1020304050"),
            "Ana",
            "Restrepo",
            LocalDate.of(1990, 3, 14),
            new Email("ana.restrepo@example.com"));

    private final List<Step> calls = new ArrayList<>();

    private record FakeRepository(Map<NationalId, Lead> leads) implements LeadRepository {
        @Override
        public Optional<Lead> findById(NationalId id) {
            return Optional.ofNullable(leads.get(id));
        }
    }

    private record FakeRegistry(List<Step> calls, RegistryOutcome outcome) implements NationalRegistry {
        @Override
        public RegistryOutcome check(Lead lead) {
            calls.add(Step.REGISTRY);
            return outcome;
        }
    }

    private record FakeJudicial(List<Step> calls, JudicialOutcome outcome) implements JudicialRecords {
        @Override
        public JudicialOutcome check(Lead lead) {
            calls.add(Step.JUDICIAL);
            return outcome;
        }
    }

    private record FakeBureau(List<Step> calls, BureauOutcome outcome) implements ComplianceBureau {
        @Override
        public BureauOutcome screen(Lead lead) {
            calls.add(Step.BUREAU);
            return outcome;
        }
    }

    private record FakeScore(List<Step> calls, ScoreOutcome outcome) implements QualificationScore {
        @Override
        public ScoreOutcome score(Lead lead) {
            calls.add(Step.SCORE);
            return outcome;
        }
    }

    private Pipeline pipeline(
            RegistryOutcome registry, JudicialOutcome judicial, BureauOutcome bureau, ScoreOutcome score) {
        return new Pipeline(
                new FakeRepository(Map.of(LEAD.id(), LEAD)),
                new FakeRegistry(calls, registry),
                new FakeJudicial(calls, judicial),
                new FakeBureau(calls, bureau),
                new FakeScore(calls, score));
    }

    private Pipeline cleanPipeline() {
        return pipeline(
                new RegistryOutcome.Matched(),
                new JudicialOutcome.Clear(),
                new BureauOutcome.Clear(),
                new ScoreOutcome.Scored(75));
    }

    @Test
    void anUnknownIdIsRejectedWithoutCallingAnyValidation() {
        var decision = cleanPipeline().validate(new NationalId("9999999999"));

        assertEquals(
                new Decision.Rejected(new RejectionCause.LeadNotInDatabase(new NationalId("9999999999"))), decision);
        assertEquals(List.of(), calls);
    }

    @Test
    void theRunStopsAtTheFirstNonCleanOutcome() {
        var decision = pipeline(
                        new RegistryOutcome.Mismatch(List.of("birthDate")),
                        new JudicialOutcome.Clear(),
                        new BureauOutcome.Clear(),
                        new ScoreOutcome.Scored(75))
                .validate(LEAD.id());

        assertEquals(new Decision.Rejected(new RejectionCause.RegistryMismatch(List.of("birthDate"))), decision);
        assertEquals(List.of(Step.REGISTRY), calls);
    }

    @Test
    void fourCleanOutcomesConvert() {
        var decision = cleanPipeline().validate(LEAD.id());

        assertEquals(new Decision.Converted(new Prospect(LEAD, 75)), decision);
        assertEquals(List.of(Step.REGISTRY, Step.JUDICIAL, Step.BUREAU, Step.SCORE), calls);
    }

    @Test
    void resumingAtTheBureauSkipsTheStepsBeforeIt() {
        var decision = cleanPipeline().run(LEAD, Step.BUREAU);

        assertEquals(new Decision.Converted(new Prospect(LEAD, 75)), decision);
        assertEquals(List.of(Step.BUREAU, Step.SCORE), calls);
    }

    @Test
    void resumingAtTheScoreReportsPendingAtTheScore() {
        var decision = pipeline(
                        new RegistryOutcome.Matched(),
                        new JudicialOutcome.Clear(),
                        new BureauOutcome.Clear(),
                        new ScoreOutcome.Unavailable("scoring offline"))
                .run(LEAD, Step.SCORE);

        assertEquals(
                new Decision.PendingManualReview(new Checkpoint(LEAD, Step.SCORE, "scoring offline")), decision);
        assertEquals(List.of(Step.SCORE), calls);
    }
}
