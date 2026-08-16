# 01. Domain types, the pipeline and four stubbed ports

## Goal

Every type in ADR 0008 exists, the four ports are interfaces with stub
implementations, and a pipeline sequences them into a `Decision`. A lead goes in
and a terminal decision comes out through the real types, including the
rejection and pending-review branches. There is no CLI yet, so the increment is
observable through tests.

## Scope

- The types of ADR 0008: `NationalId`, `Email`, `Lead`, `Prospect`, `Step`, the
  four outcome hierarchies under `StepOutcome`, `RejectionCause`, `Decision`,
  `CaseId`, `Checkpoint`, `ReviewCase`, `CaseStatus`.
- `Decisions.terminal(Lead, StepOutcome)` — the one exhaustive switch.
- The four validation ports and `LeadRepository`.
- Stub adapters returning fixed clean outcomes.
- An in-memory `LeadRepository` seeded from a hand-written map of three leads.
- `Pipeline`, sequential.

## Not in scope

- Parallelism — spec 03. Registry and judicial are called one after the other,
  in canonical order, which produces the same decisions.
- The CLI and the composition root — spec 02. Wiring here happens in tests.
- Fixtures, latency, real adapters — spec 03.
- The score threshold — spec 05. Until then the single `Scored` arm converts
  whatever the number is (ADR 0008).
- Persisting anything. `PendingManualReview` carries a `Checkpoint` and nothing
  writes it to disk — spec 06.

## Design decisions

The shapes are ADR 0008 and are not re-argued here. What this spec adds:

**Packages.** `com.addi.lead.domain` for the types and `Decisions`, `.port` for
the five interfaces, `.adapter` for the stubs, `.app` for `Pipeline`.

**`Decisions` is a final class with a private constructor and one static
method.** The domain is pure; there is nothing to instantiate.

**`Pipeline` has two entry points, not two classes:**

```java
Decision validate(NationalId id);      // lookup, then run from REGISTRY
Decision run(Lead lead, Step from);    // the resume entry (ADR 0008)
```

`validate` is the only producer of `RejectionCause.LeadNotInDatabase`; the
lookup is not a validation step and never enters the switch. Spec 06 calls
`run` with a checkpoint's step and needs no second orchestrator.

**`run` iterates `Step` values from `from` in declaration order**, calls the
matching port, passes the outcome to `Decisions.terminal`, and returns the first
non-empty result. The loop always terminates: the score step returns either a
conversion or a pending review, so there is no path off the end. Reaching the
end anyway is an invariant violation and may throw — it is not an expected
outcome and gets no case in `Decision`.

**Stubs are named `Stub*`, live in `.adapter`, and return fixed clean
outcomes**, with the score stub returning `Scored(75)`. They exist so spec 02
has something to wire and are deleted in spec 03.

**Tests drive the ports with hand-written fake records, not the stubs**, so each
arm of the switch can be reached independently and call counts can be asserted.

## Acceptance criteria

1. Each of the twelve `StepOutcome` cases maps through `Decisions.terminal` to
   the decision ADR 0008 states — twelve assertions, one per case.
2. Each of the four `Unavailable` cases yields `PendingManualReview` whose
   checkpoint names that step and carries the reason string unchanged.
3. `RegistryOutcome.NotFound` and `RegistryOutcome.Mismatch` produce different
   causes, and the mismatched field list survives into the cause unchanged.
4. `validate` with an ID absent from the repository returns
   `Rejected(LeadNotInDatabase)` and calls no validation port — asserted with
   fakes that record their invocations.
5. The pipeline stops at the first non-clean outcome: with registry returning
   `Mismatch`, the judicial, bureau and score fakes are never called.
6. Four clean outcomes with `Scored(75)` return `Converted`, and the prospect
   carries the same lead and the score 75.
7. `run(lead, Step.BUREAU)` calls the bureau and score ports and no others.
8. `run(lead, Step.SCORE)` with an unavailable score returns pending at `SCORE`,
   not at `REGISTRY`.
9. The stub adapters wired together convert a seeded lead.

## Effort

medium. ADR 0008 fixed the shapes; what is left is transcription, the sequencing
loop and the tests that pin each arm.

## Docs

Nothing. ADR 0008 is the record, no ambiguity is being closed, and no
user-facing behaviour exists yet.
