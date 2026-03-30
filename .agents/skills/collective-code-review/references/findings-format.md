# Reviewer finding format

Every reviewer should keep output compact and structured.

## If there are no meaningful issues

```text
status: no_findings
summary: The reviewed area looks sound for my lane.
```

## If there is a meaningful issue

```text
status: finding
severity: high
title: Missing transaction around hero and inventory updates
evidence: HeroRepository.save(), InventoryRepository.save(), and the calling use case update both sides in separate operations.
why_it_matters: A partial failure can persist one side but not the other, leaving the app in an inconsistent state.
suggested_direction: Wrap the related writes in one transaction or move them behind a repository method with atomic semantics.
```

## Review principles

- Prefer 0 to 3 strong findings over a long list of weak ones.
- Do not file style-only comments unless they clearly hide a maintainability or correctness risk.
- Tie every finding to evidence in the patch or directly adjacent code.
