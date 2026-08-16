package com.addi.lead.cli;

import com.addi.lead.domain.Decision;
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
              validate-lead --id <nationalId>   Qualify a lead into a prospect.

            Exit codes: 0 converted, 1 rejected, 2 pending manual review, 3 input error.""";

    /** A switch over three tokens, not a library. Gains a case per command in specs 06 and 07. */
    public sealed interface Invocation {

        record ValidateLead(NationalId id) implements Invocation {}

        /** Never reaches the decision switch, which is why {@link Decision} has no fourth case. */
        record InputError(String message) implements Invocation {}
    }

    public static Invocation parse(String[] args) {
        if (args.length == 0) {
            return new Invocation.InputError("No command given.");
        }
        return switch (args[0]) {
            case "validate-lead" -> validateLead(args);
            default -> new Invocation.InputError("Unknown command: " + args[0]);
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

    /** Gains a case in spec 05 and another in spec 06. No {@code default} to hide either. */
    private static String reason(RejectionCause cause) {
        return switch (cause) {
            case RejectionCause.LeadNotInDatabase ignored -> "not found in the local database.";
            case RejectionCause.RegistryNotFound ignored -> "not found in the national registry.";
            case RejectionCause.RegistryMismatch(var fields) -> "national registry data does not match on "
                    + String.join(", ", fields) + ".";
            case RejectionCause.JudicialRecords(var count) -> count + " judicial records found.";
            case RejectionCause.Sanctioned(var list) -> "sanctioned on the " + list + " list.";
        };
    }

    private Cli() {}
}
