---
name: guides
description: Create or update standalone Micronaut Guides in micronaut-projects/micronaut-guides, including topic discovery, guide authoring, validation, PDF export, and pull request handoff. Use for requests to create a Micronaut Guide, add a guide to micronaut-guides, author a tutorial for a Micronaut module, or prepare a guide PR with PDF.
license: Apache-2.0
compatibility: Micronaut Guides repository and Micronaut repositories that need standalone tutorial PRs
metadata:
  author: Micronaut Agent Company
  version: "1.0.0"
---

# Guides (Micronaut Maintainer)

Use this skill for standalone tutorial work in `micronaut-projects/micronaut-guides`. Start from the caller's local Micronaut repository to understand the API, module, or behavior being taught, then do the guide implementation in a current clone of `micronaut-guides`.

Do not use this skill for ordinary module documentation under `src/main/docs/guide`; use the `docs` skill for that work. Do not use it for generic non-Micronaut tutorials.

## Goal

Produce a reviewable pull request against `micronaut-projects/micronaut-guides` with source-backed guide content, validation evidence, and a PDF export of the rendered guide page for maintainers.

## Procedure

1. Define the topic from the local repository.
2. Refresh upstream `micronaut-guides` and deduplicate against existing guides.
3. Inspect current repository conventions before writing.
4. Author the smallest correct guide change.
5. Validate the guide and render the page.
6. Export a PDF and prepare the PR handoff.

### 1) Define the Topic

- Identify the exact Micronaut feature, module, API, integration, or migration path the guide should teach.
- Read the local implementation, tests, module docs, and examples that prove the behavior.
- Decide the target reader task in one sentence, for example: "Use Micronaut Serialization with a custom serializer in a controller response."
- Prefer a runnable application and tests over prose-only explanation.

### 2) Refresh and Deduplicate

Work from current upstream facts, not this skill's snapshot:

```bash
git clone https://github.com/micronaut-projects/micronaut-guides.git
cd micronaut-guides
git fetch origin
git switch master
git pull --ff-only
```

Before creating a new guide, inventory `guides/*/metadata.json` and nearby guide source. Check titles, slugs, tags, categories, `apps`, `base`, `publish`, and guide bodies for duplicates or near-duplicates. Treat `publish: false` guides as base or partial guides, not public topics, but inspect them when a new guide could reuse them.

If an existing guide already teaches the task, update that guide instead of creating a duplicate. If overlap is ambiguous, stop and ask for maintainer direction.

### 3) Inspect Current Conventions

Always read these current upstream files before editing:

- `README.md`
- `build.gradle`
- `buildSrc/src/main/java/io/micronaut/guides/core/Guide.java`
- `buildSrc/src/main/java/io/micronaut/guides/Category.java`
- `buildSrc/src/main/resources/guide-metadata.schema.json`
- Nearby guides in the same topic family

Use `references/repository-workflow.md` for clone, branch, build-output, task naming, and validation details. Use `references/guide-authoring-conventions.md` for metadata, directory layout, macros, placeholders, and base-guide rules.

### 4) Author the Guide

- Choose a kebab-case slug that matches the reader task and does not collide with existing guide directories.
- Create or update `guides/<slug>/metadata.json`.
- Use one root AsciiDoc file named `<slug>.adoc` unless current metadata conventions require `asciidoctor`.
- Put source under the expected Java, Groovy, and Kotlin layout when the guide supports multiple languages. If the feature is language-specific, set `languages` in metadata and explain why in the PR.
- For single-app guides, use an app named `default`. For multi-app guides, mirror existing app-directory patterns and declare each app in metadata.
- Use validated guide macros such as `common:`, `source:`, `resource:`, `test:`, `testResource:`, `callout:`, `external:`, `dependency:`, and `guideLink:` instead of manually duplicating generated paths.
- Use conditional blocks such as `:exclude-for-languages:` and `:exclude-for-build:` for language-specific or build-tool-specific text.
- Keep secrets out of source, generated code, rendered HTML, and the PDF. Use placeholders and documented cleanup steps for cloud resources.

### 5) Validate

Convert the guide slug from kebab-case to lowerCamelCase for dynamic tasks:

```bash
./gradlew <guideLowerCamel>Build
./gradlew <guideLowerCamel>RunTestScript
```

For broad validation or shared infrastructure changes, run:

```bash
./gradlew build
```

Generated applications are under `build/code`. Rendered HTML and site assets are under `build/dist`. For cloud, credentialed, or cost-incurring guides, do not run provisioning commands without explicit confirmation; document skipped validation and the reason.

### 6) Export PDF and Handoff

After rendering, export the reviewed HTML page to PDF. Prefer a headless browser export when available, and fall back to a manual browser "Print to PDF" export when needed. Keep the PDF as local review evidence or attach it to the PR; do not commit generated PDFs unless maintainers explicitly request that.

The PR body must include:

- Guide slug and user-facing task.
- Files changed.
- Commands run and outcomes.
- Rendered HTML path inspected.
- PDF filename and whether it is attached or linked.
- Any skipped validation, cloud costs, credentials, or cleanup notes.

See `references/pdf-export-and-pr.md` for the export workflow and PR checklist.

## Security Guardrails

- Never commit secrets, tokens, local credentials, cloud account identifiers, `.env` files, or generated PDFs containing private data.
- Ask before running commands that provision cloud resources or incur cost.
- Prefer sample placeholders for credentials and explain cleanup for created resources.
- Scrub screenshots, HTML, logs, and PDFs before attaching them to a PR.
- Do not add custom Starter features, dependencies, or buildSrc changes unless the guide topic requires them and nearby guide conventions support the change.

## Examples

Use this skill for:

- "Create a Micronaut Guide for the new serialization feature."
- "Add a guide to `micronaut-guides` for this module."
- "Author a tutorial for Micronaut Data's new repository behavior."
- "Prepare a guide PR with the rendered PDF."

Do not use this skill for:

- "Update `src/main/docs/guide/toc.yml` in this module."
- "Fix the release history page in this repository."
- "Write a generic blog post about Java."

## Validation Checklist

- [ ] Current `micronaut-guides` was cloned or fetched before writing.
- [ ] Existing guide titles, slugs, tags, categories, and `publish: false` bases were checked for duplicates.
- [ ] `README.md`, metadata schema, category source, and nearby guide examples were inspected.
- [ ] Guide source uses current metadata, directory, macro, language, and build-tool conventions.
- [ ] Single-guide build and test-script tasks were run, or skipped with a specific reason.
- [ ] Rendered HTML was inspected under `build/dist`.
- [ ] PDF export was created and named in the PR handoff.
- [ ] PR handoff explains changed files, validation, skipped steps, and any security-sensitive handling.

## References

- `references/repository-workflow.md`
- `references/guide-authoring-conventions.md`
- `references/guide-inventory.md`
- `references/pdf-export-and-pr.md`
