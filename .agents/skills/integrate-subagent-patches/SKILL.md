---
name: integrate-subagent-patches
description: >-
  Use this skill when multiple focused implementation packets must be combined into one coherent patch. Merge packet
  outputs carefully, resolve overlapping edits, preserve repository structure, and produce a concise merge ledger for
  the verifier and final reviewer.
---

# Integrate Subagent Patches

This skill is for integration after focused implementation work.

Use it when two or more subagent outputs must be combined before validation or review.

## Goals

- combine focused packet outputs into a coherent patch
- detect and resolve overlapping edits
- preserve repository conventions and ownership boundaries
- produce a compact ledger of what was integrated and what still looks risky

## When to use

Use this skill when:

- several focused implementers changed different parts of the same feature
- one packet added tests and another changed implementation
- integration itself could introduce regressions or structural drift
- the orchestrator needs a clean pre-validation patch

## When not to use

Do not use this skill when:

- there is only one implementation packet
- integration is trivial enough to describe directly without a formal merge pass
- there is no real patch to reconcile

## Integration principles

1. Preserve the smallest consistent patch.
2. Prefer repository conventions over clever local rewrites.
3. Do not expand into unrelated cleanup.
4. Treat shared-file overlap as a risk signal.
5. Leave heavy validation to the verifier stage.

## Required workflow

### 1) Gather packet outputs

Collect:

- packet summaries
- touched files
- tests added or modified
- assumptions and risks
- any requests directed at the verifier

### 2) Build an overlap map

Before merging, identify:

- files touched by more than one packet
- symbols or behaviors changed from multiple directions
- configuration or build files affected by any packet

### 3) Merge in a safe order

Prefer this order:

1. foundational structural changes
2. feature logic changes
3. tests
4. local cleanup only if required for correctness or compilation

### 4) Resolve conflicts deliberately

When overlap exists:

- preserve the user-requested behavior first
- preserve repository invariants second
- prefer the smallest resolution that keeps the patch coherent
- document the resolution in the merge ledger

### 5) Produce a merge ledger

Use `assets` from the orchestration skill if available, especially the merge ledger template.

The ledger must capture:

- integrated packets
- overlapping files
- conflict resolutions
- post-integration risks
- recommended validation focus

### 6) Hand off to validation

Do not run full Gradle validation here unless the orchestrator explicitly collapsed roles.
Normally this skill hands the integrated patch to `validate-android-change`.

## Output requirements

Return:

1. integrated scope summary
2. overlap and conflict summary
3. post-integration risk notes
4. recommended validation order
