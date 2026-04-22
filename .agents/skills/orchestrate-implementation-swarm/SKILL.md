---
name: orchestrate-implementation-swarm
description: >-
  Use this skill to execute a non-trivial accepted implementation plan through a small, focused multi-agent workflow.
  Decompose the work into ownership-based packets, keep the main thread strategic, reserve Gradle execution to a
  single verifier, integrate focused subagent outputs, and complete validation and review before presenting results.
---

# Implementation Swarm Orchestration

This is a meta-skill for execution, not a domain-specific implementation skill.

Use it when the user wants real code changes and the task is broad enough that a single implementation thread would
likely accumulate too much operational noise or mix strategic and tactical concerns.

This skill is intended to work with existing repo skills such as:

- `implement-compose-feature`
- `add-android-tests`
- `review-android-architecture`
- `review-compose-ui`
- `audit-compose-performance`
- `validate-android-change`
- `integrate-subagent-patches`
- `summarize-execution-artifacts`
- `swarm-code-review`

## When to use

Prefer this skill when at least one of these is true:

- the accepted plan is implementation-ready and the expected patch is non-trivial
- the work touches multiple modules, layers, or responsibilities
- the task naturally separates into several ownership-based packets
- the work will likely generate enough command or terminal output to pollute the main thread context
- the task benefits from explicit separation between implementer, integrator, verifier, and reviewer roles

## When not to use

Do not use this skill when:

- the change is tiny and obviously fits in one focused implementation pass
- the task is answer-only, planning-only, or investigation-only
- the work would create overlapping packets with more coordination cost than benefit
- there is no meaningful patch to integrate or validate

## Core principles

1. The orchestrator owns strategy, decomposition, integration order, and final synthesis.
2. Implementation subagents own narrow tactical scope, not the whole repository picture.
3. Prefer partitioning by ownership and file boundaries, not by arbitrary numbered steps.
4. Keep parallel implementation agents few and focused. Usually 2 to 4 is enough.
5. Raw terminal output should not accumulate in the orchestrator context.
6. Only one designated verifier should run Gradle-based validation.
7. Review remains mandatory for non-trivial patches unless the user explicitly asked to skip it.

## Inputs

- the original user request
- the accepted plan and its implementation handoff
- repository guidance from `AGENTS.md` and nearby guidance
- any repo-specific implementation or review skills relevant to the task

## Required workflow

### 1) Reconstruct the execution brief

Treat the implementation context as the combination of:

- the original user request
- the accepted plan
- the implementation handoff contract
- repository conventions

Do not treat the user's plain acceptance as a tiny new request.

### 2) Create a handoff packet

Before delegating work, build a concise execution handoff packet using `assets/handoff-packet-template.md`.

The handoff packet must capture:

- success criteria
- in-scope and out-of-scope work
- likely touch points
- repository invariants
- validation policy
- review policy
- which skills are likely relevant

### 3) Partition the work

Create 1 to 4 non-overlapping work packets using `assets/work-packet-template.md`.

Partition by:

- module ownership
- layer ownership
- file boundaries
- concern boundaries

Avoid giving two implementation subagents write ownership over the same file unless there is no reasonable alternative.

### 4) Assign roles intentionally

Possible roles:

- `implementer`
- `test_implementer`
- `integrator`
- `verifier`
- `fixer`

The same agent may fill more than one role when the task is small, but the verifier should remain the only owner of
Gradle-based validation.

### 5) Constrain implementation subagents

Implementation subagents should:

- stay within their declared owned scope
- prefer the narrowest relevant repo skill for the packet
- avoid broad refactors unless the accepted plan explicitly requires them
- avoid Gradle tasks and other heavy validation commands
- report structured results using `assets/subagent-report-template.md`

If a subagent believes compile feedback is necessary, it should request verifier-owned validation instead of running
Gradle itself.

### 6) Summarize operational output

Any substantial tool, terminal, or build output should be compressed with `summarize-execution-artifacts` before it
reaches the orchestrator.

The orchestrator should reason over compact summaries and structured findings, not raw logs.

### 7) Integrate focused outputs

If multiple implementation packets produced code changes, use `integrate-subagent-patches`.

The integrator should:

- merge the outputs
- detect overlapping edits
- resolve conflicts
- record assumptions and risks
- preserve repository conventions and ownership boundaries

### 8) Validate through a single authority

Use `validate-android-change` as the verifier-owned stage.

Validation should usually proceed from:

1. targeted, cheapest useful checks
2. broader or full verification only when warranted by scope or risk

### 9) Fix validation findings

If validation finds issues, route them back through the smallest useful correction loop:

- original packet owner when the fix is local and obvious
- integrator when the issue was introduced during merging
- a dedicated fixer when the issue spans packets

Keep the fix loop narrow. Do not restart the whole swarm unless the findings reveal a broken decomposition.

### 10) Run final review

After validation passes or reaches a reasonable stopping point, run `swarm-code-review` for non-trivial patches unless
the user explicitly asked to skip review.

Choose any additional targeted review skills dynamically based on the actual patch.

### 11) Produce the final execution ledger

Return a concise execution summary that includes:

- the work packets created
- which roles ran
- which skills were used
- what was integrated
- what validation ran
- what review ran
- remaining risks or deferred items

## Quality bar

A good orchestration pass:

- keeps the main thread strategic and readable
- avoids overlapping ownership between implementers
- centralizes Gradle validation
- preserves repository structure
- produces a patch that is easier to validate and review than a single monolithic execution trace

## Completion criteria

This skill is complete only when:

- the task was decomposed intentionally
- focused packets were executed or intentionally collapsed
- integration was handled explicitly when needed
- Gradle validation ran through a single verifier path
- review was run or explicitly skipped by user request
- the final response includes a compact execution ledger
