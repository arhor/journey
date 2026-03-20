---
name: audit-compose-performance
description: Audit Jetpack Compose code for performance issues in this repository. Use when asked to optimize a screen, reduce recomposition, investigate jank, review lazy layout behavior, or validate lifecycle, memory, and background-work handling. Do not use for pure feature implementation, generic UI polish, or test-only tasks.
---

# Purpose

Use this skill to review Compose code with a performance lens.

This skill preserves the former performance rule:
- minimize recomposition using proper keys
- use lazy loading properly
- implement efficient image loading
- use state management that avoids unnecessary updates
- follow lifecycle-aware patterns
- manage memory carefully
- perform background work in appropriate places

# What to check

## Recomposition

- identify broad state reads that may trigger unnecessary recomposition
- check whether composables receive more changing data than they need
- verify that lazy list items use stable keys where identity matters
- check whether expensive calculations happen directly in composition
- use `derivedStateOf` only where it meaningfully limits work

## Lazy layouts

- review `LazyColumn`, `LazyRow`, and related containers for:
    - missing item keys
    - expensive per-item work
    - unstable inputs
    - repeated formatting or heavy calculations in item composition

## Images and resources

- ensure image-loading approach is efficient and consistent with the project
- avoid recreating expensive image-related objects during recomposition
- be cautious with large resources on hot paths

## State management

- check whether state shape causes overly broad UI updates
- avoid unnecessary update blast radius across large composable trees
- check for mutable structures that hide change tracking or cause noisy recomposition

## Lifecycle and background work

- ensure observable state is collected in a lifecycle-aware way
- review side effects for correct ownership
- avoid long-running or heavy work on the main path
- ensure background processing follows existing coroutine conventions

## Memory

- look for avoidable object churn in hot composition paths
- avoid accidentally retaining heavy objects longer than necessary

# Audit workflow

1. Inspect the changed code or target screen.
2. Identify likely hot paths.
3. Flag meaningful issues only; avoid cargo-cult micro-optimizations.
4. For each issue, explain:
    - what is inefficient
    - why it matters
    - the smallest useful fix
5. Distinguish confirmed issues from speculative concerns.

# Output format

Return:

1. Confirmed problems
2. Likely improvements
3. Things that are acceptable and should not be over-optimized
4. Suggested fix order from highest impact to lowest
