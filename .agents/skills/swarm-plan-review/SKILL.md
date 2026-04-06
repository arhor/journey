---
name: swarm-plan-review
description: >-
  Automatically use this skill whenever the user asks for a repository-specific investigation, design proposal,
  or implementation plan that is more than a trivial one-step answer. Draft the plan, run a swarm review,
  and refine it before any code changes begin. Do not use for direct implementation tasks or trivial plans.
---

# Swarm Plan Review

This skill is for planning work, not coding work.

Use it by default when the user wants Codex to:

- investigate a request,
- understand the existing codebase,
- propose an implementation plan,
- have that plan reviewed by several specialist subagents,
- refine the plan before any code changes begin.

The user does not need to ask for swarm review explicitly. If the task is a real planning request, this review
workflow should happen automatically.

Do **not** use this skill for:

- direct implementation tasks where code should be changed now,
- tiny edits that do not need a real plan,
- generic brainstorming detached from the current repository,
- quick rough outlines when the user explicitly says to skip review,
- post-change code review of a working tree. Use the implementation `swarm-code-review` skill for that.

## Operating mode

Prefer to use this skill while Codex is in **plan mode**, but do not block on that mode.

The goal is to produce a strong, review-tested plan without modifying the repository. Read files, inspect architecture,
run safe read-only commands if needed, and stop at the plan.

Unless the user explicitly asks otherwise:

- do not edit files,
- do not generate patches,
- do not create commits,
- do not run destructive commands.

## Required workflow

Follow this sequence every time this skill is invoked.

### 1) Investigate the request and the repo

Build enough context to reason well:

- inspect the relevant modules, files, APIs, and tests,
- identify assumptions, constraints, and likely touch points,
- detect missing information or ambiguous requirements,
- if a critical unknown blocks a trustworthy plan, call it out clearly in the plan.

Do not drown the main thread with noisy exploration notes. Keep notes compact.

### 2) Draft an initial plan

Create a first-pass implementation plan using the template in `assets/plan-template.md`.

The draft should be concrete. It must name likely files, modules, interfaces, data flows, tests, risks, and validation
steps whenever the repository evidence supports that level of specificity.

The draft must also carry an explicit implementation handoff contract using the structure from
`assets/plan-template.md` so a later plan acceptance can reconstruct execution intent safely.

### 3) Run the review swarm

Spawn the following subagents **in parallel** and wait for all of them:

1. `requirements-critic`
2. `architecture-planner`
3. `risk-and-migration-reviewer`
4. `validation-strategist`

Give each subagent:

- the user's request,
- the relevant repository context,
- the current draft plan,
- explicit instructions to stay in review mode and not edit code.

Prompt `risk-and-migration-reviewer` specifically as the risk and migration reviewer for rollout, compatibility, hidden complexity,
performance, and failure modes in the draft plan.

Each reviewer must return findings using the structure from `references/plan-findings-format.md`.

### 4) Consolidate findings

Merge the reviewer outputs into a single review ledger:

- deduplicate overlapping points,
- group related comments,
- distinguish between blockers, important improvements, and nice-to-haves,
- record disagreements between reviewers.

Do not blindly follow every comment. Evaluate them.

### 5) Refine the plan

Revise the draft plan so that every reasonable point is addressed in one of these ways:

- **Accepted**: incorporated into the revised plan,
- **Rejected**: not incorporated, with a brief rationale,
- **Deferred**: left open for later, with a brief rationale.

If reviewers disagree, resolve the conflict explicitly. Prefer the option that best fits repository conventions, risk
level, and implementation cost.

### 6) Produce the final output

Return a planning package with these sections:

1. **Summary**
2. **Revised plan**
3. **Review ledger**
4. **Open questions / assumptions**
5. **Implementation handoff**
6. **Suggested next prompt for implementation**

The **Review ledger** must be concise and structured. Use the template from `assets/plan-review-ledger-template.md` if
helpful.

The **Implementation handoff** section must be concise and structured. It must include:

- `plan_status`: `implementation-ready` or `non-implementation`
- `recommended_skills`: the repo skill or skills that should be used when execution starts
- `review_policy`: whether `swarm-code-review` should run by default or be skipped because the user asked to skip review
- `acceptance_behavior`: whether a plain acceptance of the plan should start implementation or stop at investigation

When `plan_status` is `implementation-ready`, state explicitly that a plain acceptance of the plan should execute it
using the inferred skills from that handoff plus the original request context. When `plan_status` is
`non-implementation`, state explicitly that acceptance alone must not start coding.

## Quality bar

A good final plan:

- is specific to this repository,
- names concrete touch points,
- includes validation and rollback/migration thinking when relevant,
- surfaces uncertainty honestly,
- is shaped so an implementation agent could execute it with minimal ambiguity,
- makes the implementation handoff explicit enough that a plain plan acceptance can start the right work safely.

## Subagent prompting guidance

When spawning reviewers, tell them:

- they are reviewing a plan, not writing code,
- they should challenge assumptions and identify blind spots,
- they should prefer repository-specific feedback over generic advice,
- they may disagree with the draft, but must justify the disagreement concretely,
- they should keep findings crisp and actionable.

## Completion criteria

This skill is complete only when:

- an initial plan was created,
- the review swarm ran,
- the plan was refined,
- every reasonable reviewer point has an explicit disposition,
- the final answer clearly separates the revised plan from the review feedback,
- the final output includes the required implementation handoff contract.
