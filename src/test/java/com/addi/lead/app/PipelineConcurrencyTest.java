package com.addi.lead.app;

import static com.addi.lead.FixtureDir.ID;
import static com.addi.lead.FixtureDir.LEAD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.addi.lead.FixtureDir;
import com.addi.lead.adapter.FixtureComplianceBureau;
import com.addi.lead.adapter.FixtureJudicialRecords;
import com.addi.lead.adapter.FixtureNationalRegistry;
import com.addi.lead.adapter.FixtureQualificationScore;
import com.addi.lead.adapter.InMemoryLeadRepository;
import com.addi.lead.adapter.RandomNumbers;
import com.addi.lead.domain.Decision;
import com.addi.lead.domain.Step;
import com.addi.lead.port.Latency;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The fork-join point: that both branches really are in flight, and what the scope timeout does. */
class PipelineConcurrencyTest {

    @TempDir
    Path dir;

    /** Releases nobody until two callers have arrived, so sequential execution cannot get past it. */
    private record RendezvousLatency(CountDownLatch arrivals) implements Latency {
        @Override
        public void pause(Duration duration) {
            arrivals.countDown();
            try {
                if (!arrivals.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Only one branch ever arrived: they ran sequentially.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting for the sibling branch.");
            }
        }
    }

    /**
     * Not the production port. The scope timeout is wall-clock, so exercising it needs a branch that
     * really takes time and really answers to an interrupt.
     */
    private record RealTimeLatency() implements Latency {
        @Override
        public void pause(Duration duration) {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Pipeline pipeline(Duration timeout, Latency latency) {
        var config = new Config(timeout, dir, OptionalLong.of(7));
        return new Pipeline(
                config,
                InMemoryLeadRepository.fromFixtures(dir),
                new FixtureNationalRegistry(dir, latency),
                new FixtureJudicialRecords(dir, latency),
                new FixtureComplianceBureau(dir, latency),
                new FixtureQualificationScore(dir, latency, RandomNumbers.from(config.seed())));
    }

    @Test
    void registryAndJudicialAreInsideTheirCallsAtTheSameTime() {
        FixtureDir.clean(dir);

        var decision = pipeline(Duration.ofSeconds(30), new RendezvousLatency(new CountDownLatch(2)))
                .validate(LEAD.id());

        assertInstanceOf(Decision.Converted.class, decision);
    }

    @Test
    void aRegistrySlowerThanTheTimeoutIsPendingAtTheRegistry() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "registry.csv", ID + ",Ana,Restrepo,1990-03-14,10000,UP");
        FixtureDir.write(dir, "judicial.csv", ID + ",0,10000,UP");

        var decision = pipeline(Duration.ofMillis(100), new RealTimeLatency()).validate(LEAD.id());

        var pending = assertInstanceOf(Decision.PendingManualReview.class, decision);
        assertEquals(Step.REGISTRY, pending.checkpoint().pending());
    }

    @Test
    void aJudicialBranchThatFinishedFirstIsStillNotUsed() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "registry.csv", ID + ",Ana,Restrepo,1990-03-14,10000,UP");
        FixtureDir.write(dir, "judicial.csv", ID + ",4,0,UP");

        var decision = pipeline(Duration.ofMillis(100), new RealTimeLatency()).validate(LEAD.id());

        var pending = assertInstanceOf(Decision.PendingManualReview.class, decision);
        assertEquals(Step.REGISTRY, pending.checkpoint().pending());
    }
}
