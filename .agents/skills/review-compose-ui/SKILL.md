---
name: review-compose-ui
description: Review or polish Jetpack Compose UI code in this repository. Use when asked to review a screen, improve UI quality, clean up composables, or validate state handling, theming, previews, loading or error states, accessibility, and animation patterns. Do not use for deep architecture review, test-only work, or pure performance audits.
---

# Purpose

Use this skill to review or refine presentation-layer Compose UI.

This skill preserves the value of the former UI-oriented rule:
- use `remember` and `derivedStateOf` appropriately
- optimize recomposition where it affects UI quality
- keep modifier ordering intentional
- follow composable naming conventions
- implement previews where useful
- manage UI state properly
- provide loading and error states where relevant
- use `MaterialTheme`
- follow accessibility guidelines
- use animation patterns carefully

# What to review

## Compose structure

- composable names should match repo conventions
- composables should be split only when it improves readability, reuse, or testing
- state ownership should be obvious

## State

- local ephemeral UI state may stay local
- shared or screen-level state should be hoisted
- `remember` should be used intentionally
- `derivedStateOf` should be used where it avoids waste or clarifies derived UI state

## Modifiers and layout

- modifier ordering should be intentional
- layout and semantics should be readable and predictable
- large modifier chains should remain understandable

## Visual consistency

- use `MaterialTheme` and existing theme tokens
- avoid introducing ad hoc visual values when existing patterns already solve the problem

## Screen states

- loading, empty, success, and error states should be explicit when relevant
- error handling should not be improvised differently on every screen

## Previews

- add previews for reusable or visually important composables when useful
- previews should be easy to read and maintain

## Accessibility and animation

- interactive elements should expose useful semantics when relevant
- avoid color-only communication for important meaning
- animation should be intentional, readable, and not create state confusion

# Review workflow

1. Inspect the composable tree and screen state shape.
2. Review state ownership, modifier usage, theming, previews, loading/error handling, accessibility, and animation.
3. Prefer the smallest improvements with meaningful payoff.
4. Suggest larger restructuring only when the UI is difficult to reason about.

# Output format

Return:

1. Immediate issues to fix
2. Nice-to-have polish items
3. Accessibility and preview notes
4. A cleaned-up UI direction if the current approach is inconsistent
