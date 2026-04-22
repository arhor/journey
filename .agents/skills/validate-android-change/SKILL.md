---
name: validate-android-change
description: >-
  Use this skill as the single Gradle-owning validation stage for Android changes in this repository. Choose the
  cheapest targeted checks that provide real signal, escalate to broader verification when the scope or risk justifies
  it, summarize output cleanly, and hand back actionable findings only.
---

# Validate Android Change

This skill is the validation authority for Android implementation work.

Use it when a patch is ready for compile, lint, test, or build verification.

## Main rule

When an execution flow uses an orchestration pattern, this skill should be the only owner of Gradle-based validation.

Implementation subagents should not run Gradle tasks directly unless the orchestrator explicitly collapses roles for a
tiny task.

## Validation goals

- fail fast on targeted issues
- avoid redundant compile/test storms across parallel agents
- escalate to broader checks only when risk or scope warrants it
- return concise findings rather than raw logs

## Validation strategy

Prefer this order.

### 1) Start with the cheapest useful signal

Examples:

- module-scoped unit tests for touched areas
- a targeted task for a changed module
- a single affected test task after a small bug fix

Choose the most specific task the repository structure supports.

### 2) Escalate when scope warrants it

Escalate to broader checks when:

- shared modules or public contracts changed
- build logic, dependency graph, navigation, or app wiring changed
- multiple packets were integrated
- targeted validation found issues that suggest broader fallout
- the patch is close to merge-ready and should satisfy the standard repo gate

### 3) Use the repo gate when justified

The repository's main verification gate is:

```bash
./gradlew lintDebug test testDebugUnitTest assembleRelease --stacktrace
```

Use this full gate for broad or merge-ready patches unless there is a clear reason not to.

## Reporting rules

Never dump large raw logs into the main thread if a concise summary will do.

Use `summarize-execution-artifacts` or the same reporting discipline to produce:

- command run
- status
- top errors or warnings
- likely cause
- next action

## Recommended output shape

For each command:

- `command`
- `status`: `passed` | `failed`
- `why_this_command`
- `important_findings`
- `next_action`

Then include:

- `overall_status`
- `validation_coverage`
- `not_run_and_why`

## Repo-aware examples

Possible targeted validation choices may include patterns such as:

```bash
./gradlew :feature:map:testDebugUnitTest
./gradlew :feature:map:fog-of-war:testDebugUnitTest
./gradlew :data:testDebugUnitTest
./gradlew :app:lintDebug
```

Use the smallest meaningful command set first, then escalate when justified.

## Completion criteria

This skill is complete only when:

- the chosen validation depth matches the patch scope and risk
- results are summarized concisely
- remaining gaps are explicit
- the orchestrator can make the next decision without reading raw logs
