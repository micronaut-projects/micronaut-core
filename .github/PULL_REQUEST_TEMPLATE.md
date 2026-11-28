<!--
Keep the description short and scannable: a few sentences or bullets per section, no walls of text, no logs (link
to them). Delete the sections that do not apply. No tool footers or banners ("Generated with ...", badges): the
review declaration below already records AI use.
-->

Fixes #

## What changed

<!-- The change in a few bullets, as a user or maintainer sees it. Call out breaking changes and new configuration. -->

## Why

<!-- The problem it solves, or the issue that explains it. -->

## How to test

<!-- What you ran (for example ./gradlew check) and how a reviewer can check the behaviour. -->

## Screenshots

<!-- Only for visible changes, such as the guide or a UI. Delete otherwise. -->

## Checklist

- [ ] Tests added or updated, or not needed because ...
- [ ] Documentation (guide or Javadoc) updated, or not needed because ...
- [ ] Targets the right branch: fixes the next patch, features the next minor, breaking changes the next major

## Review declaration

<!--
In the Value column, keep one option and delete the others, for example "T1". Reviewers and automation read this
table. A row that still lists several options counts as not declared. Tiers (see MAINTAINING.md, "Risk tiers and AI
disclosure"): T0 build, docs, tests and synced files only; T1 internal module code; T2 public API, a change of
default behaviour, the repository's sensitive areas, or anything breaking. Pick the highest tier the change touches.
For the AI tool, name it (Claude Code, Codex, Copilot, ...), or write n/a when no AI was used.
-->

| Field                       | Value                              |
|-----------------------------|------------------------------------|
| **AI use**                  | none / assisted / agent-generated  |
| **AI tool**                 | n/a                                |
| **Risk tier**               | T0 / T1 / T2                       |
| **API or behaviour change** | yes / no                           |
