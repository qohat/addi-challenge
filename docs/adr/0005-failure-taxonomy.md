# 0005. Failure taxonomy: rejection and unavailability are different

Status: accepted
Date: 2026-08-16
Spec: bootstrap

## Context

Every step in this pipeline can fail in two unrelated ways. The registry can
report that the person's data does not match, or the registry can be
unreachable. Judicial can report a record, or time out. The bureau can report a
sanctions hit, or be down.

These look alike at the call site — both are "the step did not return a clean
result" — and the natural implementation collapses them into a boolean early and
loses the distinction. That collapse is the bug. A mismatch is a business
rejection and the lead should be told no. An outage is a statement about
infrastructure and says nothing about the lead; treating it as a rejection
rejects people because a server was restarting. The inverse error is worse: a
genuine sanctions hit routed into manual review as though the bureau had been
unavailable.

Both mistakes are invisible in tests that only assert "did not convert".

## Decision

The two kinds are different branches, all the way through.

Each step returns its own sealed type, named after its domain, with cases named
after what actually happened — `RegistryOutcome.Matched`, `NotFound`,
`Mismatch(fields)`, `Unavailable(reason)`. There is no generic `Result<T, E>`,
because pattern matching over one has nothing to say.

Business rejections carry a rejection cause and end the run rejected. Failures
of the outside world — unavailability, timeouts, translated exceptions — end the
run pending manual review (ADR 0004). A timeout is never a rejection.

The two collapse into a single decision in exactly one function, which matches
every case of every step outcome exhaustively, with no `default` branch. That
function is the most important one in the system and should be obvious on
sight.

Exceptions never cross into the domain. They are caught at the adapter boundary
and translated into an `Unavailable` case immediately, so the domain sees values
only.

## Consequences

Adding a failure case to any step breaks the one function that matches over it,
at compile time. That is the point: a new failure mode cannot quietly land in
the wrong bucket, because there is exactly one place where it could land and the
compiler will not let it be skipped.

Every step's outcome type is bespoke, which is more code than a shared
`Result`. It buys readable pattern matches and case names that mean something in
the language of the brief.

The rejection cause type gains a case when the score rule lands and another when
manual review lands, and each breaks every exhaustive match over it. That is the
design working; each site gets fixed deliberately rather than absorbed by a
catch-all.

The cost is verbosity, and the discipline only holds while nobody writes
`default`. That is why `CONTRIBUTING.md` states the prohibition with its reason
rather than as a bare rule.

## Alternatives rejected

- **A generic `Result<T, E>`** — one type for every step, so every match reads
  `Ok`/`Err` and the interesting distinction lives in a string.
- **Exceptions for failure** — moves the case list out of the type system into
  documentation, and nothing forces a caller to handle a new one.
- **A boolean plus a message** — collapses the two kinds at the point where they
  are still distinguishable, which is exactly the bug.
- **Collapsing at each call site instead of one place** — spreads the most
  important decision in the system across the codebase, where a new case can
  land in the wrong bucket in one file and the right one in another.
