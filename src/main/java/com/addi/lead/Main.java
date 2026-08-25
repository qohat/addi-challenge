package com.addi.lead;

import com.addi.lead.adapter.CachingComplianceBureau;
import com.addi.lead.adapter.FileReviewQueue;
import com.addi.lead.adapter.FixtureComplianceBureau;
import com.addi.lead.adapter.FixtureFraudCheck;
import com.addi.lead.adapter.FixtureJudicialRecords;
import com.addi.lead.adapter.FixtureNationalRegistry;
import com.addi.lead.adapter.FixtureQualificationScore;
import com.addi.lead.adapter.InMemoryLeadRepository;
import com.addi.lead.adapter.RandomNumbers;
import com.addi.lead.adapter.SleepingLatency;
import com.addi.lead.app.Config;
import com.addi.lead.app.Pipeline;
import com.addi.lead.cli.Cli;
import com.addi.lead.domain.CaseId;
import com.addi.lead.domain.CaseStatus;
import com.addi.lead.domain.Decision;
import com.addi.lead.domain.RejectionCause;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Clock;

/**
 * The composition root and the exit codes, and nothing else. stdout carries the decision line and
 * stderr carries usage and input errors, so a script can read stdout exactly when the exit code says
 * there is something to read.
 */
public final class Main {

    public static void main(String[] args) {
        System.exit(run(args, Config.defaults(), System.out, System.err));
    }

    /** Config and streams are arguments so the wired application runs in process against fixtures a test wrote. */
    static int run(String[] args, Config config, PrintStream out, PrintStream err) {
        return switch (Cli.parse(args)) {
            case Cli.Invocation.InputError(var message) -> {
                err.println(message);
                err.println(Cli.USAGE);
                yield 3;
            }
            case Cli.Invocation.ValidateLead(var id) -> {
                var decision = pipeline(config).validate(id);
                out.println(Cli.render(id, decision));
                yield record(decision, config, out, err);
            }
            case Cli.Invocation.ResolveReview(var caseId, var resolution) ->
                resolve(caseId, resolution, config, out, err);
            case Cli.Invocation.Demo ignored -> Demo.run(config, out, err);
        };
    }

    /**
     * A pending run leaves the analyst a case, on a second line so the decision line stays one line.
     * Its failure exits 4 rather than 2: exiting 2 would name a case nobody will find.
     */
    private static int record(Decision decision, Config config, PrintStream out, PrintStream err) {
        if (!(decision instanceof Decision.PendingManualReview(var checkpoint))) {
            return exitCode(decision);
        }
        try {
            out.println(Cli.opened(reviewQueue(config).open(checkpoint, Clock.systemUTC().instant())));
            return exitCode(decision);
        } catch (IOException e) {
            err.println("The review case could not be written: " + e);
            return 4;
        }
    }

    /**
     * Unknown, unreadable and already resolved are one answer and it is exit 3: a {@link Decision}
     * describes a lead's fate and none of the three is one.
     */
    private static int resolve(CaseId id, CaseStatus resolution, Config config, PrintStream out, PrintStream err) {
        var queue = reviewQueue(config);
        var found = queue.find(id).filter(reviewCase -> reviewCase.status() == CaseStatus.OPEN);
        if (found.isEmpty()) {
            err.println("No open review case " + id.value() + ".");
            return 3;
        }
        var reviewCase = found.get();
        var lead = reviewCase.checkpoint().lead();
        try {
            // Before the resume, so a crash halfway through one cannot leave a case approvable twice.
            queue.resolve(reviewCase, resolution);
        } catch (IOException e) {
            err.println("The review case could not be written: " + e);
            return 4;
        }
        return switch (resolution) {
            case APPROVED -> {
                var decision = pipeline(config).run(lead, reviewCase.checkpoint().resumeFrom());
                out.println(Cli.render(lead.id(), decision));
                yield record(decision, config, out, err);
            }
            case REJECTED -> {
                out.println(Cli.render(lead.id(), new Decision.Rejected(new RejectionCause.ReviewRejected(id))));
                yield 1;
            }
            case OPEN -> throw new IllegalStateException("review resolve never parses to OPEN");
        };
    }

    private static FileReviewQueue reviewQueue(Config config) {
        return new FileReviewQueue(config.data().resolve("review"));
    }

    /** The whole object graph, in one method, top to bottom (ADR 0001). */
    private static Pipeline pipeline(Config config) {
        var fixtures = config.fixtures();
        var latency = new SleepingLatency();
        var randomness = RandomNumbers.from(config.seed());
        var leads = InMemoryLeadRepository.fromFixtures(fixtures);
        var registry = new FixtureNationalRegistry(fixtures, latency);
        var judicial = new FixtureJudicialRecords(fixtures, latency);
        var bureau = new CachingComplianceBureau(
                new FixtureComplianceBureau(fixtures, latency),
                config.data().resolve("bureau-cache.csv"),
                config.bureauCacheTtl(),
                Clock.systemUTC());
        var fraud = new FixtureFraudCheck(fixtures, latency);
        var score = new FixtureQualificationScore(fixtures, latency, randomness);
        return new Pipeline(config, leads, registry, judicial, bureau, fraud, score);
    }

    static int exitCode(Decision decision) {
        return switch (decision) {
            case Decision.Converted ignored -> 0;
            case Decision.Rejected ignored -> 1;
            case Decision.PendingManualReview ignored -> 2;
        };
    }

    private Main() {}
}
