# 0007. Docker is a reviewer convenience, never a build dependency

Status: accepted
Date: 2026-08-16
Spec: 00

## Context

The brief does not ask for Docker. It exists because of ADR 0002: the project
targets Java 25 with `--enable-preview`, and a reviewer without that exact JDK
cannot run the artefact at all. Preview classes carry the release they were
compiled against and refuse to load on any other, so this is not the usual "it
probably works on 21" situation — it is a hard stop.

The risk is the familiar one. A Dockerfile added for convenience becomes the
only way anyone builds, then the only way the tests run, then a prerequisite for
contributing. At that point the container is not helping a reviewer, it is
hiding the build.

## Decision

Docker exists so a reviewer can run the CLI without installing a JDK 25 or
fighting preview flags. That is its entire job.

It never becomes a dependency of the build or the tests. The application always
builds and runs natively through Gradle, `./gradlew check` is the gate whether
or not Docker is installed, and CI runs that same task on the host rather than
in the image.

The build and runtime stages pin the same JDK image, because classes compiled
with `--enable-preview` only run on the release that compiled them.

## Consequences

A reviewer with Docker runs one command. A reviewer with SDKMAN runs Gradle.
Neither path is privileged and both are documented in the README.

There are two ways to run the thing, so both have to keep working, and only one
of them is exercised by CI. A drift between the pinned image and the toolchain
version in `build.gradle.kts` would not be caught automatically — the versions
are pinned in two places and have to be changed together.

Keeping Docker out of `check` means the image can break without failing a PR.
Accepted: a broken image inconveniences a reviewer, a Docker-dependent build
blocks everyone.

Runtime state is written to a mounted directory rather than into the container,
since the bureau cache and review queue are meant to survive an invocation
(ADR 0003) and a container filesystem does not.

## Alternatives rejected

- **No Docker** — anyone without JDK 25 is unable to run the submission, which
  for a submission is the wrong failure.
- **Docker as the primary build** — hides the build behind an image and makes
  the container mandatory for contributing.
- **A `jlink` or native-image distributable** — more moving parts than a
  Dockerfile, and preview features complicate both.
- **Dropping preview features to widen JDK compatibility** — gives up the
  concurrency model the problem is actually about (ADR 0002).
