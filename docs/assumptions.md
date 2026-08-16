# Assumptions

One-line calls made where the brief or the requirements were silent. A decision
that needed reasoning is an ADR instead; if an entry here starts wanting a
paragraph, it belongs in `docs/adr/`.

Format, one line each:

    - **Statement of the call.** Why, in one clause. `[tag]`

The tag is the spec that hit the ambiguity, or `[brief]` for calls made while
reading the brief, before any code existed.

## Reading the brief

- **Four validations, not three.** The brief says "three distinct validations"
  and then lists four. `[brief]`
- **Email is not part of the registry match.** A civil registry does not hold
  email addresses; it is format-validated at seed time and carried onto the
  prospect. `[brief]`
- **A national ID absent from the local database is a business rejection, not an
  input error.** The CLI syntax was valid; the business precondition was not.
  `[brief]`
- **Judicial rejects on any count of one or more.** The brief says "no records"
  with no severity dimension, so none is invented. `[brief]`
- **"Greater than 60" is read literally.** 60 rejects, 61 converts. `[brief]`
- **A bureau sanctions hit is a terminal rejection**, not a review trigger. The
  brief requires a clean bureau result before the score runs. `[brief]`
- **Nothing is idempotent across invocations.** Running the same lead twice runs
  the pipeline twice; only the bureau cache short-circuits work. `[brief]`
- **Converted prospects are not persisted.** The local database is in-memory and
  the process is single-shot, so conversion is observable through stdout and the
  exit code. `[brief]`

## Build and checks

- **No formatter in the gate.** Spotless with palantir-java-format was the call,
  and the spec's stated fallback fired: palantir-java-format cannot run on JDK
  25 at any version — its last release, 2.68.0, predates the release by three
  months and it dies with `NoSuchMethodError` on a javac internal. `[00]`

## The CLI

- **A national ID is any non-blank token without whitespace.** Everything
  stricter is a business rejection rather than an input error, so the syntactic
  rule only has to catch empty input. `[02]`
- **The exit code contract holds for the built distribution, not for `./gradlew
  run`.** Gradle reports any non-zero exit as build failure 1; the real code is
  in its message, and `build/install/lead-validation/bin/lead-validation` and the
  Docker image return it directly. `[02]`

## The simulated adapters

- **A missing fixture row means the system has nothing on that person**, not that
  it failed: registry `NotFound`, judicial and bureau `Clear`, score up with no
  latency. A missing file is a different thing and is that step's
  `Unavailable`. `[03]`
- **The registry adapter supplies a record and the comparison is a pure domain
  function.** The rule is the interesting part of the step, so it is testable
  without touching a file. `[03]`
- **The score value is never fixtured.** `score.csv` carries only latency and
  status; the number comes from the randomness port on every call. `[03]`
- **The fixture directory is `./fixtures` in the working directory, not
  `src/main/resources`.** Resources live inside the jar, where nothing can point
  a test or the demo at a different directory. `[03]`

## Bootstrap session

- **`validate-lead` takes the national ID and nothing else.** The lead already
  exists in the CRM, which is the local database, so the ID selects the record
  and every other field is read from it rather than re-supplied. `[bootstrap]`
- **Manual review exposes one command, `review resolve <case>
  --approve|--reject`.** No `list` or `show`; the queue directory is browsable
  with `ls` and `cat` and the brief asks for a simple CLI. `[bootstrap]`
- **Fixtures carry per-row latency alongside their outcome.** Otherwise no demo
  lead can be slow enough to fire the real scope timeout. `[bootstrap]`
- **The bureau cache is read at the bureau step, not as a pipeline-level
  short-circuit.** The brief fixes the dependency order. `[bootstrap]`
- **Exit codes: 0 converted, 1 rejected, 2 pending manual review, 3 input
  error.** So a script can branch on the decision without parsing text.
  `[bootstrap]`
- **A case ID is `<nationalId>-<timestamp>` and is also its filename.** A person
  can have more than one case, so the national ID alone will not do.
  `[bootstrap]`
- **Runtime state lives in a configurable directory, `./data` by default.**
  Gitignored; tests point it at a temp directory. `[bootstrap]`
- **No mocking framework.** Ports make a fake a five-line record, and a new
  dependency is an architecture decision. `[bootstrap]`
- **Registry "not found" and "data mismatch" are distinct outcome cases.** They
  are different business facts and a reviewer should see which one fired.
  `[bootstrap]`
- **stdout is human-readable prose; the exit code is the machine contract.** The
  brief asks for a simple CLI, not a JSON API. `[bootstrap]`
