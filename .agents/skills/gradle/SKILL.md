---
name: gradle
description: Execute Gradle maintainer operations for Micronaut repositories using micronaut-build internals and modern Gradle best practices. Use when users ask to diagnose build failures, maintain BOM/version catalogs, manage publishing/signing, enforce binary compatibility, or debug micronaut-build plugin behavior.
license: Apache-2.0
compatibility: Micronaut framework repositories in micronaut-projects using micronaut-build plugins
metadata:
  author: Álvaro Sánchez-Mariscal
  version: "1.0.0"
  gradle-major-version: "9.x"
mcp:
  maven-tools:
    command: docker
    args: ["run", "-i", "--rm", "arvindand/maven-tools-mcp:latest"]
---

# Gradle (Micronaut Committer)

Use this skill for Micronaut framework maintainer work. Do not default to end-user app guidance.

## Goal

Apply correct, source-backed Gradle changes in Micronaut repositories, using the `micronaut-build` plugin contracts and current Gradle best practices.

Current coverage target: repositories on Gradle major line `9.x`.

For dependency-intelligence tasks (version freshness, stability, release cadence, CVE/license checks), use the embedded `maven-tools` MCP via `skill_mcp`.

## Procedure

1. Identify build shape and active plugins.
2. Map requested work to exact `micronaut-build` plugin behavior.
3. Execute minimal maintainer-safe change.
4. Verify with targeted and aggregate Gradle tasks.

### 1) Establish build shape first

- Inspect `settings.gradle` or `settings.gradle.kts` for settings plugins and dependency management.
- Confirm `io.micronaut.build.shared.settings` is present unless the repository intentionally diverged.
- Validate settings-scoped plugins (especially Develocity wiring) in `settings.gradle(.kts)`, not in project `build.gradle(.kts)`.
- Confirm root build applies `io.micronaut.build.internal.parent` for standard aggregation behavior.
- Inspect root and impacted module build files for applied plugin IDs and `micronautBuild` extension usage.
- Inspect `buildSrc`/convention plugins before modifying module build scripts.
- Inspect `gradle.properties` and `gradle/*.versions.toml` for version source and overrides.
- Treat module directory names and Gradle project paths separately; if standardized names are enabled, use `:micronaut-*` paths for task targeting.
- If module paths are unclear, run `./gradlew projects`.
- If dependency resolution is involved, capture `dependencyInsight` before changing files.

### 2) Use source-of-truth plugin contracts

- Use `references/micronaut-build-plugins.md` as the authoritative plugin behavior map.
- Treat `micronaut-build` source as primary truth. Its README may be outdated.
- Remember default plugin IDs are internal: `io.micronaut.build.internal.<name>`.
- Important exception: `io.micronaut.build.shared.settings`.

### 3) Execute maintainer workflows

- **Build failures**: reproduce with minimal task path, then expand to module `check`, then root validation.
- **Catalog/version updates**: use plugin-provided workflows (`updateVersionCatalogs`, `useLatestVersions`) where available.
- **Catalog imports**: prefer `micronautBuild { importMicronautCatalog() }` and alias-specific imports over ad hoc BOM wiring when repository conventions use settings extension catalog import.
- **Dependency intelligence**: use the `maven-tools` MCP for latest stable versions, upgrade comparisons, and dependency health signals before changing catalogs or BOM constraints.
- **Dependency scope hygiene**: choose `api` only for public API surface; use `implementation` for internals; use `compileOnly`/`runtimeOnly` intentionally.
- **BOM/publishing**: verify BOM generation, inlining behavior, publication metadata, and signing gates.
- **Binary compatibility**: keep baseline discovery and accepted API changes explicit; never silently suppress regressions.
- **Docs/quality**: treat root-only aggregator plugins and docs pipelines as first-class checks.
- **Layering**: prefer convention plugin changes (`buildSrc`) over duplicating logic in many module build files.

### 4) Verify before completion

- Run at least one targeted task and one aggregate task:
  - `./gradlew :<module>:check`
  - `./gradlew check`
- For maintainer pipelines, include docs and style gates where applicable:
  - `./gradlew check docs`
  - `./gradlew spotlessApply spotlessCheck`
- For API-affecting changes, run compatibility checks:
  - `./gradlew japiCmp`
- For newly added published modules, define when binary compatibility checks start in module build logic (example):

```kotlin
micronautBuild {
    binaryCompatibility.enabledAfter("2.0.0")
}
```

- Use the repository's intended first compatible release version instead of blindly reusing `2.0.0`.
- Add focused diagnostics when needed:
  - `./gradlew dependencyInsight --configuration <conf> --dependency <module>`
  - `./gradlew --scan <task>`
- Report exact commands and outcomes.

## Guardrails

- Do not use `enforcedPlatform` when plugin logic explicitly forbids it.
- Do not bypass Micronaut version mismatch checks except intentional development-version flows.
- Do not apply root-only plugins to subprojects.
- Do not replace framework maintainer workflows with generic app-centric shortcuts.

## Best Practices

Use `references/gradle-best-practices.md` for operational standards on:

- configuration and build cache behavior,
- provider/lazy configuration patterns,
- toolchains and reproducibility,
- secure publishing and dependency verification,
- CI reliability.

## Examples

- "Diagnose why micronaut-core version mismatch fails on compileClasspath."
- "Update version catalogs safely and apply latest allowed minor versions."
- "Fix Sonatype release pipeline behavior for a non-snapshot release."
- "Investigate binary compatibility failure and validate accepted changes file usage."

## Validation Checklist

- [ ] Active plugins identified from build files.
- [ ] Decisions mapped to `micronaut-build` source behavior.
- [ ] Changes align with maintainer workflows.
- [ ] Verification tasks run and outcomes captured.
- [ ] No end-user-only Gradle guidance substituted for framework operations.

## References

- `references/micronaut-build-plugins.md`
- `references/gradle-best-practices.md`
