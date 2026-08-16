# Code style

Java 25, single Gradle module, no framework. Every rule carries its reason,
because a rule with a reason gets applied correctly in cases nobody anticipated
and a bare prohibition gets applied literally and wrongly.

Read this when there is Java to write. Not while planning, reading tests or
writing docs.

## Types

**Sealed interfaces plus records for anything that has cases.** The compiler
then knows the case list, which is the only static check this design leans on.

**Exhaustive `switch`, never a `default` on a closed type.** A catch-all throws
that check away. When a new case breaks twenty call sites, that is the design
working, and each site gets fixed deliberately.

**No generic `Result<T, E>`.** Each step returns its own type, named after its
domain, with cases named after what actually happened. Matching on
`RegistryOutcome.Mismatch(var fields)` says something; matching on `Err(var e)`
says nothing.

**Illegal states unrepresentable.** A validated thing and an unvalidated thing
are different types. The type system is cheaper than any test.

**No nulls in the domain, and total functions.** Parse, don't validate: input
crosses the boundary once, becomes a valid type, and everything downstream
assumes it is valid.

**Immutable by default.** Records, `List.copyOf` on the way in, no setters, no
mutable collection escaping its boundary, no shared mutable state across
parallel branches. Mutation that is genuinely needed lives behind a port.

## Errors

**Errors are values.** Every expected outcome is a named case in a sealed
hierarchy, returned. Nothing expected is ever signalled by throwing. Modelling
the failures is most of the design, because what happens when something does not
work is the interesting part of the system.

**Exceptions only for the genuinely unrecoverable**, and never crossing into the
domain. Caught at the boundary, translated into a domain value immediately.

**Custom exceptions carry no stacktrace:** `super(message, null, false, false)`.
Filling one in is expensive and nobody is going to read it.

**Domain failure and outside-world failure are different branches.** A
dependency being down is not a business rejection. Where the two collapse into
one decision, that collapse happens in exactly one exhaustively matched
function — usually the most important function in the system, and it should be
obvious which one it is.

## Effects

**The domain is pure**: no I/O, no clock, no randomness, no logging. Every
effect enters through a port and executes at the boundary. If something is hard
to test, an effect leaked where it should not have.

**Wiring is by hand in one composition root.** No annotations, no
autoconfiguration, no reflection. The whole object graph should be readable in
one file.

**Concurrency stays in the orchestration layer.** Scopes and futures are
effectful and do not compose as pure values, so the domain never sees a task, a
future or a thread. Concurrency exceptions are caught and converted before
anything escapes.

**Java 25 structured concurrency is opened with a factory:**

    try (var scope = StructuredTaskScope.open()) {
        var registry = scope.fork(() -> ...);
        var judicial = scope.fork(() -> ...);
        scope.join();
        use(registry.get(), judicial.get());
    }

The Java 21 shape — `new StructuredTaskScope<>()` with `ShutdownOnFailure`
subclasses — does not compile here. Writing it is a training-data artefact, not
a choice.

## Dependencies

**Plain language features over libraries.** If the language has what is needed,
using it directly says more than importing someone else's abstraction.

**No new dependency without asking.** It is an architecture decision.

## Tests

**Tests first, always.** Written from the spec, watched to fail, then the code
that makes them pass. A test that has never failed has proved nothing.

**Hand-written fakes implementing the ports. No mocking framework.** Ports make
a fake a five-line record, and a mocking framework would be a dependency buying
nothing.

**Deterministic by construction.** The domain is pure and every effect is an
injected port, so clock, randomness and latency take fixed values in tests. No
retries, no sleeps, no timing tolerances.

## Prose in code

**A paragraph of comment defending an awkward construct means the design is
wrong.** Fix the design, not the comment. A mutable local in an argument parser
once carried five lines of justification; the fix was to carry the field name on
the error value.

**No emojis anywhere** — code, output, commits, PRs.

**English everywhere.**
