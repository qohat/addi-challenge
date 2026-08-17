---
name: run-spec
description: Close out the previous spec and implement the next one in a worktree, tests first, ending at an open PR. Use at the start of a session that is going to build a spec, or when the user asks to run, execute or implement a spec.
---

# Run a spec

One spec, one session. Close the previous one first, then build this one.

## 1. Close out the previous spec

Do this first and always. A session's log is only complete once that session has
closed, which is why the closeout happens here rather than at the end of the
session that did the work.

    python3 scripts/cost.py --list             find the previous session id
    python3 scripts/cost.py --session <id>     price it

Then:

- Add the spec to `docs/ai/cost.md`: a summary row, and the breakdown table the
  script prints, under its own heading. With one session per spec the figure
  measures session overhead as much as implementation; say so rather than
  pretending otherwise.
- Set the previous spec's row in `specs/README.md` to `merged`.
- Confirm `docs/ai/sessions/NN-slug.txt` exists for it. If it does not, stop and
  say so — the export is a deliverable, not a nicety.

Skip this section only when there is no previous spec.

## 2. Set up

    git worktree add .trees/NN-slug -b NN-slug

Read the spec. Read `CONTRIBUTING.md` now — not earlier, and not again.

## 3. Build it

Tests first, always. Written from the spec's acceptance criteria, run, watched
to fail, then the code that makes them pass. A test that has never failed has
proved nothing.

Work through the acceptance criteria in order. When one is ambiguous, resolve it
against `docs/PROJECT_CONTEXT.md` and `docs/assumptions.md`; if it is still
ambiguous, ask rather than guess, and add the answer to `docs/assumptions.md`
tagged with this spec.

`./gradlew check` must pass — format, build, unit tests, integration tests and
the context budget. That task is the gate.

## 4. Document only what the spec says to

The spec's Docs section decides. If it says nothing is needed, write nothing.
Otherwise: a decision with reasoning becomes an ADR from `docs/adr/0000-template.md`;
a one-line call where the brief was silent becomes a line in
`docs/assumptions.md` tagged with this spec.

## 5. Hand it over

Split the work into reviewable commits — the domain types, the adapter, the
wiring, the docs. Not one commit, and not one per file. Open the PR and stop.
The author reads and merges it; nothing advances without that.

## 6. Write the next spec

Once the author has merged the PR, write the next `not started` row in the queue
with `/create-spec`, committed straight to `main` — no worktree, no code. This
step is not optional and it is what ends the session: a session that implements a
spec and leaves the queue unwritten hands the next one an empty desk.

It happens here, after the merge, because the next spec is written from the code
as it now is rather than as it was planned. A shape that came out different, a
type that gained a case, a helper that already exists — the spec written before
the merge would have missed all of it.

Except on a cheaper model. A `low` spec runs on Sonnet; that session stops after
the export and writes no spec. Specs are written by the planning model, and Opus
writes the next one in its own session. Two reasons: the session would mix models
against the one-model-per-session rule, and its cost row would stop being the
Sonnet-versus-Opus comparison that running the spec on Sonnet existed to produce
— a planning-shaped Opus spec priced into a Sonnet implementation measures
neither.

## 7. Export

Export the session to `docs/ai/sessions/NN-slug.txt` before closing it. The
export directory is part of the submission, so the way the work is done and the
way it is submitted are the same thing.

## Stop conditions

- Past roughly forty requests, or about to *implement* a second spec: stop and
  open a new session. Writing the next spec in step 6 is not that — one spec
  built, the next one specified, is exactly how a session is supposed to end,
  unless this is a cheaper-model session, which ends one step earlier.
- The spec turns out to be wrong: stop, say so, and fix the spec on `main`
  rather than improvising in the worktree.
- A new dependency looks necessary: ask first. That is an architecture decision.
