---
name: swarm-code-review
description: >-
  Automatically use this skill for substantive implementation tasks that modify repository code. Implement the
  change, run the configured local multi-agent review in parallel, resolve every reasonable finding, and summarize
  what was fixed or rebutted. Do not use for pure explanation tasks, trivial no-code edits, or when the user
  explicitly asks to skip review.
---

# Swarm Code Review

Use this skill by default whenever you make a non-trivial code change or feature behavior change and there is a real
patch to inspect. The user does not need to ask for swarm review explicitly.

If execution starts from accepting an immediately preceding plan, treat the accepted plan plus the original user
request as the implementation context. Use this skill whenever that reconstructed task produces a non-trivial patch,
even if the acceptance turn itself only says `yes` or `proceed`.

If another implementation skill is also active, do the implementation work first, then run this review workflow before
you present the final result.

## Goal

Implement the requested change, then run a focused review swarm against your own patch. Treat reviewers like strong
teammates, not oracles. You must address every reasonable finding, but you do **not** need to obey every suggestion.
Thoughtful disagreement is allowed when supported by repository conventions, constraints, tests, or architecture.

## Inputs

- A user task that requires code changes.
- The current repository state.
- Any project guidance in `AGENTS.md`, nearby `AGENTS.md` files, and any review-specific documents referenced from them.

## When *not* to use this skill

Do not use this skill when:

- the task is answer-only, exploratory, or documentation-only
- the user explicitly asks to skip review or only wants a rough draft
- the change is so tiny that a review swarm would add noise rather than signal
- there is no code diff and no behavioral change to inspect

## Required workflow

### 1) Implement first

Do the implementation work normally.

Before spawning reviewers, make sure you have:

- understood the requested scope
- identified the likely affected files and code paths
- made the code changes
- run at least the most targeted validation you can reasonably run for the changed area

### 2) Spawn the review subagents in parallel

After the implementation exists, explicitly spawn these read-only subagents in parallel:

- `change_mapper`
- `correctness_reviewer`
- `test_reviewer`
- `architecture_reviewer`

If one of these agents is unavailable, continue with the others and report the gap.

### 3) Give each subagent a narrow job

#### `change_mapper`

Ask it to map the changed execution path and identify assumptions, touched layers, and risk hotspots. It should not
propose broad refactors.

#### `correctness_reviewer`

Ask it to look for concrete correctness, regression, security, concurrency, data integrity, performance, and
API-contract problems introduced by the patch.

#### `test_reviewer`

Ask it to inspect what tests exist, what changed behavior is still untested, and whether the current tests are fragile,
incomplete, or misleading.

#### `architecture_reviewer`

Ask it to inspect maintainability, layering, coupling, naming clarity, interface design, and consistency with repository
conventions. It should avoid style-only nitpicks unless they hide a real maintenance hazard.

### 4) Require structured findings

Ask every reviewer to return findings in this shape:

- `status`: `finding` or `no_findings`
- `severity`: `critical`, `high`, `medium`, or `low` when `status=finding`
- `title`: a concise issue summary
- `evidence`: specific files, symbols, scenarios, or commands
- `why_it_matters`: impact in practical terms
- `suggested_direction`: small, concrete next step

Reviewers should prefer fewer, stronger findings over long nit lists.

### 5) Consolidate and deduplicate

After the subagents return:

- merge overlapping findings
- drop duplicates
- ignore weak style-only comments unless they meaningfully affect comprehension, correctness, maintenance, or future
  bugs
- prioritize comments that are evidence-backed and actionable

### 6) Resolve every reasonable finding

For each reasonable finding, choose exactly one resolution state:

- `fixed`: you changed code or tests to address it
- `rebutted`: you intentionally declined the suggestion and wrote a reasoned rebuttal
- `deferred`: only allowed when truly out of scope or explicitly postponed by the user; explain why

A finding is **not** addressed until it has one of those states with a concrete explanation.

### 7) Re-validate after fixes

After applying warranted fixes:

- rerun the most relevant tests, linters, or build steps for the changed area
- if full validation is too expensive, run the best targeted checks available and say what you did not run
- if a rebutted finding depends on an assumption, verify that assumption when feasible

### 8) Final response requirements

Your final task summary must include a compact swarm code review section with:

- which reviewer agents ran
- how many findings were raised
- how many were fixed, rebutted, or deferred
- the most important fixes made during review
- any remaining concerns or intentionally deferred items

## Resolution standard

Use this standard when deciding whether to accept or rebut a comment:

Accept the comment when it is evidence-backed and meaningfully improves correctness, resilience, test coverage,
maintainability, or user-visible behavior.

Rebut the comment when:

- it conflicts with explicit user requirements
- it conflicts with repository conventions or `AGENTS.md`
- it proposes a larger refactor than the task justifies
- the underlying concern is valid but the suggested fix is not
- the reviewer misunderstood the code, the diff, or a project constraint

When rebutting, do not be defensive. State the concern, explain why you are not changing the code, and reference the
relevant constraint or evidence.

## Preferred parent-agent prompt to the reviewers

Use wording close to this:

> Review the current patch only. Return only strong, actionable findings. Do not suggest broad rewrites. If you think
> the patch is fine in your area, say `no_findings`.

## Notes

- Review the patch that exists now, not an idealized redesign.
- Keep review latency proportional to the size of the change.
- The goal is a disciplined local code review, not a committee opera.
