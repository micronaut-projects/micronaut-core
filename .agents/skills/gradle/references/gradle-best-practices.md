# Gradle Best Practices for Micronaut Maintainers

This guidance targets framework committers maintaining many repositories and CI pipelines.

Current coverage target: Gradle major line `9.x`.

## Version Scope Policy

1. Treat the repository wrapper major line as authoritative (`gradle/wrapper/gradle-wrapper.properties`).
2. Avoid hard-coding patch-specific guidance in shared maintainer docs.
3. Use `docs.gradle.org/current` references as the baseline unless a repository pins a behavior.

## Core Principles

1. Prefer deterministic, cache-friendly builds.
2. Keep configuration lazy and provider-driven.
3. Centralize versions and avoid drift.
4. Validate changes with targeted plus aggregate tasks.

## Configuration and Performance

- Prefer task configuration avoidance (`tasks.register`, `configureEach`) over eager task realization.
- Keep task inputs/outputs explicit and stable for cacheability.
- Avoid absolute paths in cache-sensitive task inputs whenever possible.
- Prefer providers (`providers.gradleProperty`, `providers.environmentVariable`, `Provider` chaining) over imperative reads.
- Use build scans in CI triage (`--scan`) to identify config cache/caching misses and slow paths.
- Prefer convention plugin ownership over broad root `subprojects {}` mutation.

## Configuration Cache and Build Cache

- Keep custom task logic free from unsafe runtime access to mutable project state.
- Use provider-backed values for environment/system properties.
- Treat non-cache-compatible tasks as exceptional and document why they are excluded.
- For CI reproducibility, keep caching policy explicit in settings and workflows.

## Version Management

- Keep dependency versions in `gradle/*.versions.toml` catalogs.
- Prefer catalog aliases and platform/BOM alignment instead of ad hoc version literals.
- For Micronaut repos, maintain explicit core/bom alignment and investigate mismatch failures with `dependencyInsight`.
- Use automated catalog update workflows with clear policy for major/minor/prerelease boundaries.
- Use `java-library` API/implementation boundaries in reusable modules to minimize downstream compile classpaths.
- Where `micronautBuild` settings extension is the convention, prefer `importMicronautCatalog()` / `importMicronautCatalog("<alias>")` over one-off dependency-management wiring.

## Java and Toolchains

- Prefer Java toolchains for reproducible compilation and test execution.
- Keep `javaVersion` and test JVM version strategy explicit in build extension configuration.
- Avoid mixing legacy source/target compatibility settings unless migration requires it.

## Publishing and Release Safety

- Keep credentials and signing material in env/system properties; never commit secrets.
- Validate publication metadata (name, description, SCM, developers) before release.
- Distinguish snapshot and release behaviors and verify staging/publish flow accordingly.
- Ensure release pipelines run pre-release checks and artifact verification before publication.

## Quality Gates

- Enforce style and static checks in normal `check` workflows.
- Keep test reporting readable and deterministic in CI.
- Aggregate coverage and quality reporting from root where plugin design expects it.
- Treat binary compatibility checks as release blockers unless explicitly accepted and documented.

## Diagnostics Patterns

- Dependency source tracing:

```bash
./gradlew dependencyInsight --configuration compileClasspath --dependency io.micronaut:micronaut-core
```

- Targeted task reproduction:

```bash
./gradlew :<module>:check --stacktrace
```

- Aggregate validation before merge:

```bash
./gradlew check
```

## Anti-Patterns to Avoid

- Hard-coding dependency versions in many module build files.
- Disabling guard checks to get green CI without root-cause analysis.
- Applying root-only plugins on subprojects.
- Making broad Gradle changes without a narrow reproduction and verification plan.

## References

- Gradle User Manual: https://docs.gradle.org/current/userguide/userguide.html
- Gradle Best Practices: https://docs.gradle.org/current/userguide/best_practices.html
- Build Cache: https://docs.gradle.org/current/userguide/build_cache.html
- Configuration Cache: https://docs.gradle.org/current/userguide/configuration_cache.html
- Toolchains: https://docs.gradle.org/current/userguide/toolchains.html
- Version Catalogs: https://docs.gradle.org/current/userguide/version_catalogs.html
- Dependency Verification: https://docs.gradle.org/current/userguide/dependency_verification.html
