# 0004. Manual review is a resume, not an override

Status: accepted
Date: 2026-08-16
Spec: bootstrap

## Context

The brief says: "If the service is down, demonstrate resilience by gracefully
handling the failure or triggering a manual review flow." That is the entire
specification. It names the flow and defines nothing about it — not what a case
contains, not what approving means, not what happens afterwards.

The obvious reading is that an analyst approves a lead and the lead converts.
That reading is wrong in a way worth writing down. The bureau being unreachable
says nothing about whether the person is sanctioned, and it says nothing at all
about their qualification score. If approval converted the lead, an outage would
become a route around the score rule — the one rule the brief states in
unambiguous terms. Resilience would have turned into a bypass.

The alternative reading is that the analyst is substituting for the step that
failed, not for the decision. They verified the bureau out of band; that is one
input, not the outcome.

## Decision

A manual review case is a checkpoint, not a ticket. It persists the lead, the
outcomes already resolved, why the run stopped, and which step is pending. The
pending step is the first unresolved step in canonical order, which means a
timeout that killed both parallel branches needs no special representation.

`review resolve <case> --approve` means: the analyst verified the failed step out
of band and it came back clean. The pipeline resumes from the checkpoint with
that outcome injected, runs the remaining steps, and can still end rejected —
only the score rule converts a lead. `--reject` closes the case.

The same orchestrator serves both paths. A resumed run is an ordinary run
entering with a pre-resolved prefix, so there is no second code path to keep in
step with the first.

An approval is never written to the bureau cache. An analyst decision is not a
bureau response and must not expire on a TTL.

Approving a pending score step re-runs the score port. An analyst cannot attest
a number, and the rule still applies to whatever comes back.

A resolved case is terminal. A resumed run that hits another unavailable
dependency produces a new case with a later checkpoint rather than reopening the
old one.

Manual review is a product feature serving an analyst at Addi. It is verified by
tests like everything else and never exercised by hand.

## Consequences

An outage cannot convert a lead that the rules would reject, which is the
property that made this worth deciding.

Having one orchestrator serve both paths means the resume logic is a matter of
seeding the pipeline rather than duplicating it, and every test of the normal
path is also a test of the resumed path's tail.

The checkpoint has to be serialisable, which constrains the outcome types: they
must be plain data with no captured effects. That is a constraint the pure
domain already imposes, so it costs nothing here, but it is now load-bearing.

Terminal cases plus new cases on repeat failure means a lead can accumulate
several cases. The case ID carries a timestamp for exactly that reason
(ADR 0003).

The cost is that an analyst cannot force a conversion, which in a real product
someone would eventually ask for. It would be a different feature with a
different name and a different audit trail, not a flag on this one.

## Alternatives rejected

- **Approval converts the lead** — turns a dependency outage into a route around
  the score rule.
- **Approval injects a score** — an analyst cannot attest a random number
  produced by an internal system.
- **Re-run the whole pipeline on resume** — discards the checkpoint, so the
  parallel stage runs twice and a lead can get a different answer on the second
  pass for reasons unrelated to the review.
- **A separate resume orchestrator** — two code paths that have to be kept
  equivalent, and they will not be.
- **Fail closed, no review at all** — permitted by the brief's "or", but it
  demonstrates the least interesting half of the requirement.
