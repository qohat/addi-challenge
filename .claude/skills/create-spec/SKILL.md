---
name: create-spec
description: Write the next spec in specs/README.md and commit it to main on its own. Use once the previous spec has merged and before implementing the next one, or when the user asks to write, draft or plan a spec.
---

# Write a spec

A spec is a version of the code that could be deployed the day it merges. Write
it once the previous spec has merged, when the shape of the code is actually
known, and before implementing it.

## Read, in this order, and nothing else

1. `specs/README.md` — the queue. Take the next `not started` row.
2. `docs/PROJECT_CONTEXT.md` — the problem and what is already decided.
3. `git log --oneline -15` — what just happened.
4. `docs/assumptions.md` — do not re-open a closed ambiguity.
5. The previous spec, if this one continues it.

Do not read the codebase to write a spec. If a shape is genuinely unknown, Grep
for the one symbol. A spec that needed a survey of the source is a spec that is
doing the implementation's job.

## Write `specs/NN-slug.md`

Under 150 lines, enforced by `python3 scripts/context-budget.py`. Over that, it
is either doing the implementation's job or it should be two specs. Sections:

- **Goal** — one paragraph. What runs after this merges that did not before.
- **Scope** — what is built, in the vocabulary of the domain, not of Java.
- **Not in scope** — what a reasonable reader would expect here and will not
  get, with the spec number that has it instead.
- **Design decisions** — every shape this spec fixes: types, interfaces, data
  formats, control flow. Anything left open here is designed by whoever executes
  it, and later specs will sit on their choice.
- **Acceptance criteria** — numbered, each one a test that can be written before
  any code exists. Behaviour, not implementation. If a criterion cannot fail,
  it is not a criterion.
- **Effort** — high, medium or low, by the rule in `specs/README.md`.
- **Docs** — whether this spec needs an ADR, an assumptions line, or a README
  change, and why. "No, nothing" is a valid and common answer.

## Then

1. Update the spec's row in `specs/README.md`: status `specced`.
2. `python3 scripts/context-budget.py` must exit 0.
3. Commit to `main`, the spec and the queue row only. No code, no worktree. The
   spec commit lands before its implementation exists, because that ordering is
   part of what is being submitted.

## Do not

- Do not write code, tests, or a worktree.
- Do not write more than one spec.
- Do not invent a requirement the brief and `docs/PROJECT_CONTEXT.md` are silent
  on. Ask, and record the answer in `docs/assumptions.md` tagged with this spec.
