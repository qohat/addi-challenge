# 00. Build, checks and CI

## Goal

A clean clone runs `./gradlew check` and it passes: Java 25 with preview
enabled, formatting, tests and the context budgets, in one task. CI runs that
same task and nothing else. A reviewer without JDK 25 runs the application from
a Docker image. No domain code exists yet — what merges is the gate that
everything after it has to pass.

## Scope

- Gradle wrapper, single module, application plugin.
- Java 25 toolchain with `--enable-preview` at compile, test and run.
- JUnit 5.
- A formatting check inside `check`.
- The context budget script inside `check`.
- A placeholder `main` that prints the application name and exits 0.
- A GitHub Actions workflow running `./gradlew check`.
- A multi-stage Dockerfile.
- `.gitignore` for build output and `./data`.

## Not in scope

- Domain types, ports, the pipeline — spec 01.
- Argument parsing and the exit code contract — spec 02.
- Fixtures and adapters — spec 03.

## Design decisions

**`settings.gradle.kts` is written before `gradle wrapper` runs.** Gradle 9
refuses to generate a wrapper in a directory that contains no build and fails
with "does not contain a Gradle build".

**Preview is enabled in three places, not one.** `JavaCompile` compiler args,
`Test` JVM args, and `application.applicationDefaultJvmArgs`. Missing any one of
them fails at a different phase, which is why they are listed here rather than
left to be discovered.

**The toolchain is pinned** with `java.toolchain.languageVersion`, not taken
from the ambient JDK, so CI and Docker cannot drift from the developer machine.
Classes compiled with preview only run on the same release, so the pin is
load-bearing (ADR 0002).

**Formatting is Spotless with palantir-java-format**, build-time only and never
on the runtime classpath. If it cannot parse Java 25 sources, the fallback is to
delete the plugin and drop "format" from the gate in `AGENTS.md` — not to spend
a session fighting a formatter.

**Budgets are enforced by the build.** A task running
`python3 scripts/context-budget.py` is wired into `check`, and a non-zero exit
fails it. A budget that only a human runs is a budget that drifts.

**Layout.** Single module, root package `com.addi.lead`, sources in
`src/main/java`, tests in `src/test/java`.

**Docker** is multi-stage on a JDK 25 base for both stages, with
`--enable-preview` in the entrypoint. Nothing in the Gradle build or any test
refers to it (ADR 0007).

**CI** is one workflow, one job: `actions/setup-java` with Temurin 25, then
`./gradlew check`. No matrix — one JDK is the only one that works.

## Acceptance criteria

1. `./gradlew check` exits 0 on a clean clone where only the wrapper is
   committed.
2. A test asserting `Runtime.version().feature() == 25` passes.
3. A test opens `StructuredTaskScope.open()`, forks two subtasks returning
   constants, joins, and reads both values. It fails on a JVM without
   `--enable-preview`, which is what makes it worth writing.
4. `./gradlew run` prints the application name and exits 0.
5. `./gradlew check` fails when a file exceeds its line budget, verified by
   temporarily padding `CLAUDE.md` and watching the build go red.
6. The formatting check fails on a deliberately misformatted file and passes
   after the formatter runs.
7. The CI workflow runs `./gradlew check` and is green on this spec's PR.
8. `docker build .` succeeds and `docker run` prints what `./gradlew run`
   prints.

## Effort

medium. Every shape is fixed. Getting preview into all three phases and a
wrapper into an empty directory needs care, not design.

## Docs

One line in `docs/assumptions.md` tagged `[00]`: Spotless with
palantir-java-format is the formatter, build-time only, with the stated
fallback. No ADR — ADR 0002 covers preview and ADR 0007 covers Docker. No
README change; the README is spec 08.
