# Plan findings format

Each reviewer should return findings in this shape:

## Overall verdict

One short paragraph describing whether the draft plan is directionally sound.

## Findings

### [severity] short title

- Area: requirement | architecture | data model | API | migration | testing | rollout | performance | observability |
  security | DX | unknowns
- Why it matters: one or two sentences
- Suggested adjustment: one or two sentences
- Evidence: files, modules, interfaces, tests, or patterns from the repo that support the point

Severity values:

- blocker
- major
- moderate
- minor

## Missing information

List critical unknowns that limit confidence.

## What is already strong

List the strongest parts of the draft plan so the main agent preserves them during revision.
