package com.addi.lead;

import com.addi.lead.adapter.FixtureComplianceBureau;
import com.addi.lead.adapter.FixtureJudicialRecords;
import com.addi.lead.adapter.FixtureNationalRegistry;
import com.addi.lead.adapter.FixtureQualificationScore;
import com.addi.lead.adapter.InMemoryLeadRepository;
import com.addi.lead.adapter.RandomNumbers;
import com.addi.lead.adapter.SleepingLatency;
import com.addi.lead.app.Config;
import com.addi.lead.app.Pipeline;
import com.addi.lead.cli.Cli;
import com.addi.lead.domain.Decision;
import java.io.PrintStream;

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
                yield exitCode(decision);
            }
        };
    }

    /** The whole object graph, in one method, top to bottom (ADR 0001). */
    private static Pipeline pipeline(Config config) {
        var fixtures = config.fixtures();
        var latency = new SleepingLatency();
        var randomness = RandomNumbers.from(config.seed());
        var leads = InMemoryLeadRepository.fromFixtures(fixtures);
        var registry = new FixtureNationalRegistry(fixtures, latency);
        var judicial = new FixtureJudicialRecords(fixtures, latency);
        var bureau = new FixtureComplianceBureau(fixtures, latency);
        var score = new FixtureQualificationScore(fixtures, latency, randomness);
        return new Pipeline(config, leads, registry, judicial, bureau, score);
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
