---
name: add-android-tests
description: Add or review tests for Jetpack Compose UI, ViewModels, and UseCases in this repository. Use when asked to add tests for new features, bug fixes, refactors, or weakly covered code. Do not use for pure UI polish, architecture-only review, or performance-only investigation.
---

# Purpose

Use this skill to decide what tests should be added and to add the right level of tests.

This skill preserves the former testing rule:
- write unit tests for ViewModels and UseCases
- add Compose UI tests where user-visible behavior needs protection
- use fake repositories when appropriate
- aim for meaningful coverage
- use proper coroutine test dispatchers

# Testing principles

- prefer the smallest set of tests that meaningfully protects behavior
- test behavior and state transitions, not implementation trivia
- reuse existing repo testing patterns
- keep fast logic tests in JVM source sets

# What to test

## ViewModels

- UI state transitions
- loading, success, empty, and error behavior when relevant
- important user actions and side effects coordinated by the ViewModel

## UseCases

- business rules
- edge cases
- success and failure behavior where meaningful

## Compose UI

- rendered user-visible content
- interactions
- visibility changes
- enabled/disabled state
- other meaningful behavior exposed through semantics

## Test doubles

- prefer fake repositories where they keep tests clearer
- avoid over-mocking when a fake is simpler
- keep doubles focused on the behavior needed by the test

# Repo-specific conventions

- put tests in the module that owns the code
- prefer backtick test names in the form `{function/method/action} should {expected behavior} when {given context}`
- split tests visually into `// Given`, `// When`, and `// Then`
- use repo-standard test stack and coroutine test helpers
- keep root `connectedDebugAndroidTest` healthy if adding instrumentation coverage

# Test selection workflow

1. Inspect the changed code.
2. Decide which layers are affected:
    - ViewModel
    - UseCase
    - Compose UI
3. Add the minimum combination of tests that protects the change.
4. Prefer unit tests first for logic-heavy changes.
5. Add UI tests when user-visible behavior would otherwise be left unprotected.
6. Cover success, failure, and notable edge cases when relevant.

# Output format

When using this skill, provide:

1. Which test layers were chosen and why
2. The added or updated tests
3. Any missing follow-up coverage
4. The Gradle command or commands that should be run to validate the tests
