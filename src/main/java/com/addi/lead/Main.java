package com.addi.lead;

import com.addi.lead.adapter.InMemoryLeadRepository;
import com.addi.lead.adapter.StubComplianceBureau;
import com.addi.lead.adapter.StubJudicialRecords;
import com.addi.lead.adapter.StubNationalRegistry;
import com.addi.lead.adapter.StubQualificationScore;
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
        System.exit(run(args, System.out, System.err));
    }

    /** Streams are arguments so the wired application can be run in process against captured ones. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        return switch (Cli.parse(args)) {
            case Cli.Invocation.InputError(var message) -> {
                err.println(message);
                err.println(Cli.USAGE);
                yield 3;
            }
            case Cli.Invocation.ValidateLead(var id) -> {
                var decision = pipeline().validate(id);
                out.println(Cli.render(id, decision));
                yield exitCode(decision);
            }
        };
    }

    /** The whole object graph, in one method, top to bottom (ADR 0001). The stubs go in spec 03. */
    private static Pipeline pipeline() {
        var leads = InMemoryLeadRepository.seeded();
        var registry = new StubNationalRegistry();
        var judicial = new StubJudicialRecords();
        var bureau = new StubComplianceBureau();
        var score = new StubQualificationScore();
        return new Pipeline(leads, registry, judicial, bureau, score);
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
