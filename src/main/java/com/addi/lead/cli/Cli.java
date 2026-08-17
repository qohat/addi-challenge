package com.addi.lead.cli;

import com.addi.lead.domain.CaseId;
import com.addi.lead.domain.CaseStatus;
import com.addi.lead.domain.Decision;
import com.addi.lead.domain.Decisions;
import com.addi.lead.domain.NationalId;
import com.addi.lead.domain.RejectionCause;

/**
 * The whole boundary: a command line in, a line of prose out. Both are pure functions, so the CLI is
 * tested without spawning a JVM and {@link com.addi.lead.Main} keeps nothing but the wiring and the
 * exit codes.
 */
public final class Cli {

    public static final String USAGE =
            """
            Usage:
              validate-lead --id <nationalId>              Qualify a lead into a prospect.
              review resolve <case> --approve|--reject     Resolve a case an analyst has reviewed.
              demo                                         Run every outcome against the fixtures.

            Exit codes: 0 converted, 1 rejected, 2 pending manual review, 3 input error,
            4 the review case could not be written.""";

    /** A switch over a handful of tokens, not a library. One case per command. */
    public sealed interface Invocation {

        record ValidateLead(NationalId id) implements Invocation {}

        /** The status the case file gets, which is what a boolean here would not have been. */
        record ResolveReview(CaseId caseId, CaseStatus resolution) implements Invocation {}

        /** No fields: a demo a reviewer has to configure is a worse demo. */
        record Demo() implements Invocation {}

        /** Never reaches the decision switch, which is why {@link Decision} has no fourth case. */
        record InputError(String message) implements Invocation {}
    }

    public static Invocation parse(String[] args) {
        if (args.length == 0) {
            return new Invocation.InputError("No command given.");
        }
        return switch (args[0]) {
            case "validate-lead" -> validateLead(args);
            case "review" -> review(args);
            case "demo" -> args.length == 1
                    ? new Invocation.Demo()
                    : new Invocation.InputError("demo takes no arguments.");
            default -> new Invocation.InputError("Unknown command: " + args[0]);
        };
    }

    /** Four tokens exactly. {@code resolve} is the only subcommand: listing a directory is not one. */
    private static Invocation review(String[] args) {
        if (args.length != 4 || !"resolve".equals(args[1])) {
            return new Invocation.InputError("review takes exactly resolve <case> --approve or --reject.");
        }
        return switch (args[3]) {
            case "--approve" -> new Invocation.ResolveReview(new CaseId(args[2]), CaseStatus.APPROVED);
            case "--reject" -> new Invocation.ResolveReview(new CaseId(args[2]), CaseStatus.REJECTED);
            default -> new Invocation.InputError("A case is resolved with --approve or --reject.");
        };
    }

    private static Invocation validateLead(String[] args) {
        if (args.length != 3 || !"--id".equals(args[1])) {
            return new Invocation.InputError("validate-lead takes exactly --id <nationalId>.");
        }
        return NationalId.parse(args[2])
                .<Invocation>map(Invocation.ValidateLead::new)
                .orElseGet(() -> new Invocation.InputError("A national ID must be one token with no whitespace."));
    }

    /**
     * The requested id rather than the decision's, because a rejection carries its cause and not
     * always the lead: the registry can reject an id the caller named and nothing else holds it.
     */
    public static String render(NationalId id, Decision decision) {
        return "Lead " + id.value() + " " + switch (decision) {
            case Decision.Converted(var prospect) -> "converted to prospect. Score " + prospect.score() + ".";
            case Decision.Rejected(var cause) -> "rejected: " + reason(cause);
            case Decision.PendingManualReview(var checkpoint) -> "pending manual review at step "
                    + checkpoint.pending() + ": " + checkpoint.reason() + ".";
        };
    }

    /** A second line, so {@link #render} stays one decision in one line and keeps its signature. */
    public static String opened(CaseId caseId) {
        return "Case " + caseId.value() + " opened.";
    }

    /** Gains a case in spec 05 and another in spec 06. No {@code default} to hide either. */
    private static String reason(RejectionCause cause) {
        return switch (cause) {
            case RejectionCause.LeadNotInDatabase ignored -> "not found in the local database.";
            case RejectionCause.RegistryNotFound ignored -> "not found in the national registry.";
            case RejectionCause.RegistryMismatch(var fields) -> "national registry data does not match on "
                    + String.join(", ", fields) + ".";
            case RejectionCause.JudicialRecords(var count) -> count + " judicial records found.";
            case RejectionCause.Sanctioned(var list) -> "sanctioned on the " + list + " list.";
            case RejectionCause.ScoreTooLow(var value) ->
                    "qualification score " + value + " is not above " + Decisions.MINIMUM_SCORE + ".";
            case RejectionCause.ReviewRejected(var caseId) ->
                    "manual review case " + caseId.value() + " was rejected by an analyst.";
        };
    }

    private Cli() {}
}
