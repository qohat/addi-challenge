# 0001. Hexagonal architecture with a hand-wired composition root

Status: accepted
Date: 2026-08-16
Spec: bootstrap

## Context

The brief asks for a CLI that orchestrates four external systems, none of which
exist. Every one of them has to be simulated now and could plausibly be real
later, which is the textbook case for putting each behind a port.

The reflex on the JVM is Spring Boot. It would supply dependency injection,
configuration binding and a lifecycle for free. But the exercise is a
demonstration of design, and a framework hides exactly the part being assessed:
what the boundaries are, what crosses them, and where effects execute. An
annotation that wires a bean does not show a reviewer anything. Framework
startup also dwarfs the work in a single-shot CLI, and it drags reflection into
a project that otherwise compiles to something a reader can follow end to end.

## Decision

Hexagonal architecture. The domain holds pure types and rules; every effect —
the four external systems, the local database, the clock, randomness, latency,
the cache and the review queue — enters through a port and is executed by an
adapter at the boundary.

No framework, no annotations, no autoconfiguration, no reflection. The object
graph is constructed by hand in a single composition root, so the entire wiring
of the application is readable in one file. Configuration is explicit and passed
as values.

## Consequences

The dependency graph is visible in one place, and a reviewer can follow a lead
from `main` to a decision without knowing a framework's conventions. Tests need
no container: a port is an interface, a fake is a record, and no mocking
framework is required.

The cost is manual work. Adding an adapter means editing the composition root,
and there is no configuration binding, no profile mechanism and no lifecycle
management. At this size that is a few lines; in a larger system it would stop
scaling and this ADR would need superseding.

Nothing here is transferable to a Spring codebase without rework, which is a
real cost if this ever grew into a service.

## Alternatives rejected

- **Spring Boot** — hides the boundaries the exercise is meant to demonstrate,
  and pays framework startup on every single-shot CLI invocation.
- **A lightweight DI container (Dagger, Guice)** — still reflection or codegen
  for a graph small enough to read in one screen.
- **Layered architecture (controller / service / repository)** — the orthodoxy,
  but it gives no place to state that the domain is pure, which is the property
  this design is built on.
