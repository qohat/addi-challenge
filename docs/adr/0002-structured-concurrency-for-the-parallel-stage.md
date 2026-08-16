# 0002. Java 25 preview structured concurrency for the parallel stage

Status: accepted
Date: 2026-08-16
Spec: bootstrap

## Context

The brief makes parallelism a hard requirement: registry and judicial are
non-dependent and must execute in parallel, and the bureau requires both to have
succeeded. So there is exactly one fork-join point in the system, and it has
three properties worth getting right — both branches must run concurrently, a
failure or timeout in one must not leave the other running, and neither branch
may outlive the call that started it.

`CompletableFuture` can express this, but cancellation is manual and leaks are
easy: an `allOf` that completes exceptionally leaves the other future running
unless something explicitly cancels it. A bare `ExecutorService` is worse, since
the lifetime of a submitted task has no relationship to the lifetime of the
scope that submitted it.

`StructuredTaskScope` makes the relationship structural: the scope owns the
forks, closing it joins or cancels them, and the branches cannot escape the
try-with-resources block. In Java 25 it is still a preview API, so the build
needs `--enable-preview` at compile and at run, and classes compiled with it
only run on the same release.

## Decision

Use `StructuredTaskScope` for the registry and judicial fan-out, opened with the
Java 25 factory API:

    try (var scope = StructuredTaskScope.open()) {
        var registry = scope.fork(() -> ...);
        var judicial = scope.fork(() -> ...);
        scope.join();
        use(registry.get(), judicial.get());
    }

Enable preview features on the toolchain, and pin the same JDK release in every
build and runtime stage, Docker included.

Apply the timeout at the scope, not per branch, defaulting to 2s and
configurable. On timeout the scope cancels both branches and both report a
timeout, which routes to manual review under ADR 0005. A partial result is not
useful when the next step requires both.

Concurrency stays in the orchestration layer. The domain never sees a task, a
future or a thread, and concurrency exceptions are caught and converted into
domain values before anything escapes the scope.

## Consequences

Cancellation and lifetime are enforced by the language, not by discipline, so
the fan-out cannot leak a running branch. The code reads like the diagram in the
brief.

Preview is the price. `--enable-preview` is needed everywhere, the API can
change in Java 26, and a reviewer with a different JDK cannot run the artefact —
which is most of why Docker exists (ADR 0007).

The API shape is also a trap. The Java 21 form, `new StructuredTaskScope<>()`
with `ShutdownOnFailure` subclasses, does not compile against Java 25 and is
what a model will reach for by default. This was verified on the target machine
before starting: two 300ms branches complete in 307ms, so the parallelism is
real and not an artefact of the API being a wrapper.

## Alternatives rejected

- **`CompletableFuture.allOf`** — expresses the fan-out but not the lifetime;
  cancelling the sibling branch on failure is manual and easy to forget.
- **A plain `ExecutorService` with `invokeAll`** — a submitted task's lifetime is
  unrelated to the caller's, which is the leak this design is avoiding.
- **Virtual threads joined by hand** — the same thing with the bookkeeping
  written out longhand.
- **Sequential execution** — the brief forbids it.
