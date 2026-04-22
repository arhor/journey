---
name: summarize-execution-artifacts
description: >-
  Use this skill to compress noisy terminal output, build logs, test logs, and tool traces into short structured
  summaries that preserve signal and discard operational sludge. Prefer this whenever raw output would otherwise pollute
  the main reasoning thread.
---

# Summarize Execution Artifacts

This skill is for context hygiene.

Use it whenever a command, tool, or validation step produces enough output that the main reasoning thread would become
harder to use if the raw text were kept verbatim.

## Goal

Transform raw execution artifacts into a compact, actionable summary.

## Inputs

- terminal output
- Gradle output
- test output
- linter output
- stack traces
- tool traces
- diff summaries

## Rules

1. Preserve signal, remove noise.
2. Prefer findings and next actions over line-by-line retelling.
3. Keep exact error text only when wording matters for diagnosis.
4. Group repeated failures together.
5. Separate actionable warnings from harmless chatter.
6. Never pretend output was clean if important warnings or failures existed.

## Required output shape

### Header
- `source`
- `command` or `tool`
- `status`

### Key findings
- the 1 to 5 most important facts

### Likely cause
- concise diagnosis when grounded
- otherwise say uncertainty explicitly

### Noise intentionally omitted
- repeated download lines
- repeated task banners
- long stack traces already captured by the key finding
- verbose success chatter

### Recommended next action
- one concrete next step
- or `none` if the output is already conclusive

## Examples of good compression

Instead of:
- dozens of lines of Gradle task output

Prefer:
- `:feature:map:testDebugUnitTest` failed
- one test failed in `MapViewModelTest`
- failure indicates expected `Output.Success` but actual state was `Output.Failure`
- next step: inspect state mapping in `MapViewModel.buildUiState`

Instead of:
- a full lint log

Prefer:
- lint failed in `feature/map/.../MapScreen.kt`
- issue type: unused parameter / missing content description / wrong API level guard
- next step: fix the reported symbol and rerun the targeted lint task

## Completion criteria

This skill is complete when a downstream orchestrator can make the next decision without reading the raw output.
