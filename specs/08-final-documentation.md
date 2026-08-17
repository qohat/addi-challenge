# 08. Final documentation

## Goal

`README.md` stops being a placeholder and becomes the front door the brief's
section 5 asks for: what the system is, how a reviewer runs it, how it was
built, what working with AI actually cost and produced, and what is still
missing. After this merges the submission is complete — every deliverable the
brief names exists and the README says where it is.

## Scope

- `README.md`, rewritten whole.
- `ReadmeTest`, the one runnable check that the README does not drift from the
  CLI or the repository.
- Whatever the rewrite finds stale in the pointers it makes: a path that moved,
  a command that changed. Fix it where it is wrong.

## Not in scope

- Any change to business code, ports, adapters or fixtures. A README that needs
  the code to change is reporting a bug, and this spec reports it rather than
  fixing it.
- Rewriting `docs/PROJECT_CONTEXT.md`, `docs/adr/`, `docs/assumptions.md` or
  `specs/README.md` into the README. They stay where they are and the README
  points at them.
- Editing the plan sections of `specs/README.md` — "If time runs out" and "Why
  this order" are a record of what was planned, not a claim about what is true
  now, and rewriting them after the fact would destroy the evidence.
- Spec 08's own closeout row in `docs/ai/cost.md`. A session cannot price
  itself; a final bookkeeping session does it, and the README says so where it
  quotes the total.

## Design decisions

**The README is not context-budgeted.** Every other document in the repository
has a line limit because a model re-reads it; the README is read once, by a
person, and the brief asks for comprehensive. Target under 200 lines anyway —
a reviewer reads it whole — but no entry in `scripts/context-budget.py`.

**It carries in full only what lives nowhere else, and links the rest.** The
process narrative, the AI account, the pending improvements and the run
instructions are written here because there is no other home for them. The
decisions, the assumptions, the specs and the transcripts have homes, and the
README summarises each in a sentence and links it. Duplicating them would create
a second source of truth that goes stale first.

**Nine sections, in this order:**

1. **What it is** — one paragraph, the pipeline, the four validations.
2. **Run it** — `demo` first, because it shows every outcome without the
   reviewer knowing an ID. Then `validate-lead` and `review resolve`, the exit
   code table, the Docker path, and `./gradlew check`. Every command shown as it
   is actually invoked, from the installed distribution, with the caveat of
   assumption `[02]` about `./gradlew run` stated once.
3. **How it works** — the parallel stage and its timeout, the bureau cache,
   manual review as a resume, the score rule. A table of the eight ADRs, title
   and one clause each.
4. **Decisions and assumptions** — the four-versus-three reading and the two or
   three calls a reviewer is most likely to challenge, then a link to
   `docs/assumptions.md` for the rest.
5. **How it was built** — spec-driven, one spec per session, specs committed
   before their implementation, and where the git history shows that. The
   context layer — `CLAUDE.md`, `AGENTS.md`, the budgets, the skills — described
   as what it is: an attempt to make sessions repeatable rather than heroic.
6. **Working with AI** — what worked and what did not, each claim carrying its
   evidence. At minimum: the spec-05-on-Sonnet experiment and what it showed
   about token volume versus per-token price; the spec 06 planning split that
   cost $10.52 across two sessions and the change to `/run-spec` that followed;
   and at least one thing the model got wrong that the tests or the gate caught.
   No claim without a number or a commit behind it.
7. **What it cost** — the totals from `docs/ai/cost.md`, quoted as of spec 07
   with 08 named as not yet priced, and a link.
8. **Pending improvements** — a list, each entry one line of what and one clause
   of why it was not done. Sourced from the specs' "Not in scope" sections and
   from known ceilings in the code, not invented. Honest about the ones that are
   real gaps rather than deliberate scope cuts.
9. **Repository map** — the table already in the placeholder, corrected.

**`ReadmeTest` checks two things and nothing else.** Both catch drift a reader
would not notice; neither reviews prose.

- Every line in the README that begins `$ lead-validation ` parses through the
  real `Cli.parse` to something that is not an `InputError`.
- Every repository path the README names in backticks — anything matching a path
  with a `/` or a known top-level file — exists on disk.

## Acceptance criteria

`ReadmeTest`:

1. Every `$ lead-validation ` line in `README.md` parses to a non-`InputError`
   invocation. The test fails if a documented flag does not exist.
2. Every backticked repository path in `README.md` exists. The test fails on a
   pointer to a file that was renamed or never written.

By reading the merged `README.md`:

3. All nine sections above are present, in that order.
4. Section 2's commands are the invocations the CLI accepts, the exit code table
   is 0/1/2/3 plus 4 for a bookkeeping failure, and the Docker instructions run
   without a JDK installed.
5. Section 6 makes at least three claims about working with AI, each with a
   number, a commit or a file behind it, and at least one of them is negative.
6. Section 8 lists the pending improvements, each traceable to a spec's "Not in
   scope" or to something visible in the code, and does not present a deliberate
   scope cut as an oversight.
7. The README nowhere contradicts `docs/PROJECT_CONTEXT.md`, `docs/assumptions.md`
   or `specs/README.md`. Where it summarises them it uses their own words for the
   decision.
8. Nothing in the README describes behaviour the code does not have. Every
   outcome it claims is reachable by a command in section 2.

`./gradlew check` passes, including the context budget, which the README does not
enter.

## Effort

medium. Every shape is fixed here — the nine sections, what each holds, what the
test checks, what is not touched. The judgement left is the prose, and the
honesty of section 6 and section 8.

## Docs

One line in `docs/assumptions.md` tagged `[08]`: the README is not
context-budgeted, because it is read once by a person rather than every session
by a model, and the brief asks for comprehensive. No ADR — this spec decides
nothing the code can see. The README is itself the documentation deliverable.
