# 0008. Domain shapes and the single collapse point

Status: accepted
Date: 2026-08-16
Spec: 01

## Context

ADR 0005 fixed the failure taxonomy in principle: a bespoke outcome type per
step, rejection and unavailability on different branches, and one exhaustive
function where the two collapse into a decision. It never wrote the types down.

That gap is why spec 01 is rated high. Whoever executed it would be inventing
the shapes the remaining seven specs sit on, under implementation pressure, and
a shape invented that way is a shape nobody argued about.

The gap also hides the property worth having. "An unavailable dependency routes
to manual review" is a sentence everyone agrees with and nobody can check.
Written as one `switch` with no `default` over a closed hierarchy, the compiler
checks it on every build and refuses to let a new failure mode skip it.

So the types are decided here, before any of them exist. Types and cases only;
the specs write the bodies.

## Decision

### Lead and prospect

```java
record NationalId(String value) {}
record Email(String value) {}

record Lead(NationalId id, String firstName, String lastName,
            LocalDate birthDate, Email email) {}

record Prospect(Lead lead, int score) {}
```

A `Lead` is a CRM record: it exists in the local database and its fields are
well formed. Nothing about it has been checked against the outside world.

A `Prospect` is a lead plus the score that converted it. The difference is
evidence, not a status field. It is constructed at exactly one site — the
conversion arm of the switch below — so a prospect that did not pass all four
validations cannot be built by accident. Its invariant is `score > 60`.

`NationalId` and `Email` are wrappers because input crosses the boundary once
and is valid from there on. The ID is also the key of the fixtures, the bureau
cache and the case ID, and a bare `String` in all four places is one typo from a
silent miss.

### The steps

```java
enum Step { REGISTRY, JUDICIAL, BUREAU, SCORE }
```

Declaration order is the canonical order. Registry and judicial execute in
parallel (ADR 0002) but their outcomes are consumed in this order, which is what
makes "the first unresolved step" well defined for a checkpoint.

### Per-step outcomes

```java
sealed interface StepOutcome
        permits RegistryOutcome, JudicialOutcome, BureauOutcome, ScoreOutcome {}

sealed interface RegistryOutcome extends StepOutcome {
    record Matched()                             implements RegistryOutcome {}
    record NotFound()                            implements RegistryOutcome {}
    record Mismatch(List<String> fields)         implements RegistryOutcome {}
    record Unavailable(String reason)            implements RegistryOutcome {}
}

sealed interface JudicialOutcome extends StepOutcome {
    record Clear()                               implements JudicialOutcome {}
    record RecordsFound(int count)               implements JudicialOutcome {}
    record Unavailable(String reason)            implements JudicialOutcome {}
}

sealed interface BureauOutcome extends StepOutcome {
    record Clear()                               implements BureauOutcome {}
    record Sanctioned(String list)               implements BureauOutcome {}
    record Unavailable(String reason)            implements BureauOutcome {}
}

sealed interface ScoreOutcome extends StepOutcome {
    record Scored(int value)                     implements ScoreOutcome {}
    record Unavailable(String reason)            implements ScoreOutcome {}
}
```

`StepOutcome` is a marker with no cases of its own and no type parameter. It
exists so the collapse is one switch instead of four. It is not the generic
`Result<T, E>` that ADR 0005 rejects: there is no shared success/failure
vocabulary, every leaf keeps a name from the brief, and nothing is ever matched
at the `StepOutcome` level except the one function below.

`ScoreOutcome` carries the number, not the verdict. The `> 60` rule lives at the
single decision site where a reviewer will look for it, not in the adapter that
rolled the dice.

### Rejection cause

```java
sealed interface RejectionCause {
    record LeadNotInDatabase(NationalId id)      implements RejectionCause {}
    record RegistryNotFound()                    implements RejectionCause {}
    record RegistryMismatch(List<String> fields) implements RejectionCause {}
    record JudicialRecords(int count)            implements RejectionCause {}
    record Sanctioned(String list)               implements RejectionCause {}
}
```

Spec 05 adds `ScoreBelowThreshold(int score)`. Spec 06 adds
`ReviewRejected(CaseId caseId)`, produced by `review resolve --reject`, not by
the switch. Each addition breaks every exhaustive match over the type, which is
the design working.

`LeadNotInDatabase` is not a step outcome. The lookup is not a validation, so
its miss is constructed at the pipeline entry and never enters the switch.

### Terminal decision

```java
sealed interface Decision {
    record Converted(Prospect prospect)              implements Decision {}
    record Rejected(RejectionCause cause)            implements Decision {}
    record PendingManualReview(Checkpoint checkpoint) implements Decision {}
}
```

Three cases, three exit codes: 0, 1, 2. Exit code 3 is absent on purpose — an
unparseable command line never reaches the domain, so an input error is not a
decision about a lead.

### Checkpoint

```java
record CaseId(String value) {}

record Checkpoint(Lead lead, Step pending, String reason) {}

record ReviewCase(CaseId id, Instant openedAt,
                  Checkpoint checkpoint, CaseStatus status) {}

enum CaseStatus { OPEN, APPROVED, REJECTED }
```

ADR 0004 says a case persists the outcomes already resolved. It does — `pending`
determines them. A step that resolved before the checkpoint resolved cleanly, or
the run would have ended there; `pending = BUREAU` therefore means `Matched` and
`Clear`, and there is nothing else it could mean. Storing the prefix as well
would be a second copy that can disagree with `pending`, which is a
representable illegal state. So the checkpoint stores the step.

`CaseStatus` exists because a resolved case is terminal (ADR 0004) and that has
to be enforceable on a file that is still on disk.

### Ports

```java
interface NationalRegistry   { RegistryOutcome check(Lead lead); }
interface JudicialRecords    { JudicialOutcome check(Lead lead); }
interface ComplianceBureau   { BureauOutcome   screen(Lead lead); }
interface QualificationScore { ScoreOutcome    score(Lead lead); }
```

One shape: lead in, that step's outcome out. Judicial and score only need the
ID, and are handed the whole lead anyway so there is one signature to remember
and the resume path feeds them exactly what the first run did.

All four are synchronous and blocking. No port returns a future: the fan-out
belongs to the orchestrator (ADR 0002), and a port that knew about concurrency
would drag it into the domain.

Spec 01 needs a fifth port, which is not one of the four validations and whose
storage ADR 0003 already fixed:

```java
interface LeadRepository { Optional<Lead> findById(NationalId id); }
```

### The pipeline entry

```java
Decision run(Lead lead, Step from);
```

A fresh run passes `Step.REGISTRY`. A resume passes the checkpoint's `pending`,
and because the resolved prefix is implied by that step there is nothing to
inject and no second orchestrator (ADR 0004). Spec 03 forks registry and
judicial inside this call; spec 06 is the only caller that ever passes anything
but `REGISTRY`.

### The collapse

One function. No `default`. Every case of every step outcome, in one place.

```java
static Optional<Decision> terminal(Lead lead, StepOutcome outcome) {
    return switch (outcome) {
        case RegistryOutcome.Matched ignored      -> Optional.empty();
        case RegistryOutcome.NotFound ignored     -> rejected(new RegistryNotFound());
        case RegistryOutcome.Mismatch(var fields) -> rejected(new RegistryMismatch(fields));
        case RegistryOutcome.Unavailable(var why) -> review(lead, Step.REGISTRY, why);

        case JudicialOutcome.Clear ignored        -> Optional.empty();
        case JudicialOutcome.RecordsFound(var n)  -> rejected(new JudicialRecords(n));
        case JudicialOutcome.Unavailable(var why) -> review(lead, Step.JUDICIAL, why);

        case BureauOutcome.Clear ignored          -> Optional.empty();
        case BureauOutcome.Sanctioned(var list)   -> rejected(new Sanctioned(list));
        case BureauOutcome.Unavailable(var why)   -> review(lead, Step.BUREAU, why);

        case ScoreOutcome.Scored(var v) when v > 60 -> converted(lead, v);
        case ScoreOutcome.Scored(var v)             -> rejected(below(v));  // spec 05
        case ScoreOutcome.Unavailable(var why)    -> review(lead, Step.SCORE, why);
    };
}
```

`rejected`, `review` and `converted` are one-line constructors wrapped in
`Optional.of`, not logic. `Optional.empty()` means the step was clean and the
pipeline runs the next one; the emptiness is the only "continue" signal in the
system, so no `Pending` case pollutes `Decision`.

**Where unavailability becomes manual review**: four of these arms, one per
step, and nowhere else. Each names its own step as a literal, because the case
that matched already says which step it came from — that is how the checkpoint
gets its `pending` value without the caller passing one in. Nothing else in the
system constructs `PendingManualReview`.

Until spec 05 the two `Scored` arms are one arm that converts unconditionally.
Splitting it, and adding the cause it rejects with, is spec 05's whole job.

## Consequences

A new failure mode has exactly one place it can land, and the compiler stops the
build until it lands there. Adding `RegistryOutcome.Suspended` breaks
`terminal`, not twenty call sites that each guessed.

Thirteen arms in one function is long, and it should stay long. It is the map of
the system; splitting it for cosmetic reasons puts the two halves out of sync.

`StepOutcome` is the price of one switch instead of four. It buys nothing else
and should never gain a method.

The checkpoint is small enough to read with `cat`, and cannot contradict itself.
The cost is that a reader has to know the canonical order to see what already
passed, since the file names only the pending step.

Whether the bureau answer came from the cache is deliberately not a
`BureauOutcome` case: it is not a different decision, and putting it there would
force every match over the type to handle a distinction the domain does not
care about. If spec 04 needs to show a hit, it comes from the cache port.

`Prospect` has no ID and no timestamp because nothing persists it. If conversion
ever needs to be recorded, that is a new port and a change here.

The weakest part is `reason`: a `String` on four `Unavailable` cases and on the
checkpoint. It is display text and nothing ever branches on it. The moment
something does, it becomes a type.

Handing all four ports the whole `Lead` over-supplies two of them. Accepted for
one port shape instead of three.

## Alternatives rejected

- **A `decide` per step** — four switches, so a new case can land in the right
  bucket in one file and the wrong one in another. ADR 0005 rejects this in
  principle; this ADR is what makes the single switch typecheck.
- **Storing the resolved outcomes in the checkpoint** — a second copy of what
  `pending` already says, free to disagree with it.
- **A `Continue` or `Pending` case on `Decision`** — makes "not finished" a
  terminal decision, and every consumer then handles a case that can never map
  to an exit code.
- **A `StepVerdict` sealed type instead of `Optional<Decision>`** — two cases to
  say what `Optional` already says.
- **`Qualified` / `BelowThreshold` cases on `ScoreOutcome`** — moves the one
  business rule the brief states exactly into the adapter that generated the
  number, and away from the site where a reviewer looks for it.
- **`Prospect extends Lead`, or a status field on `Lead`** — a lead with a
  status becomes a prospect by assignment. Two types make conversion a
  construction, which is the point.
- **A shared `Unavailable` type across the four hierarchies** — saves three
  record declarations and costs the pattern its step, which is exactly the
  information the checkpoint needs.
