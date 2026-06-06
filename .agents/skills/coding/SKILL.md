---
name: coding
description: Implement and review Java code changes for Micronaut framework repositories using maintainer standards, including JSpecify null-safety conventions. Use when users ask to add or refactor Java code, fix framework bugs, evolve internal APIs, or prepare committer-ready changes with tests and verification.
license: Apache-2.0
compatibility: Micronaut framework repositories in micronaut-projects generated from micronaut-project-template
metadata:
  author: Álvaro Sánchez-Mariscal
  version: "1.0.0"
---

# Coding (Micronaut Committer)

Use this skill for maintainer-facing Java implementation work in Micronaut repositories. Do not default to end-user application shortcuts.

## Goal

Deliver minimal, source-backed Java changes that preserve framework quality: binary compatibility, JSpecify null-safety, reflection-free behavior, and full Gradle verification (`check`, `docs`, and compatibility checks when API-facing).

## Trigger Examples

Should trigger:

- "Implement this Micronaut Java feature in `src/main/java` and keep API compatibility."
- "Refactor this module internals and mark non-public APIs correctly."
- "Fix failing framework tests and prepare committer-ready validation output."
- "Add configuration support using Micronaut conventions, not app-level shortcuts."

Should not trigger:

- "Explain Micronaut basics to a beginner."
- "Create an end-user sample app from scratch."
- "Only edit release notes/changelog text."

## Procedure

1. Establish scope and API impact.
2. Implement Java code with Micronaut maintainer conventions.
3. Enforce API boundaries and binary compatibility.
4. Document Java APIs and implementation intent.
5. Keep Gradle/build changes aligned with repository conventions.
6. Verify with maintainer-grade checks before completion.

### 1) Establish scope and API impact

- Identify affected modules and whether any change is public API or internal-only.
- Inspect existing package patterns before editing (imports, nullability style, tests, naming).
- For API-facing edits, plan compatibility checks up front (`japiCmp`).
- Keep change surface minimal; avoid opportunistic refactors unless required.

### 2) Implement Java with Micronaut maintainer conventions

- Prefer modern Java idioms where they improve clarity (records, sealed types, pattern matching, `var` for local inference), but only when supported by the repository toolchain/target level.
- Do not use fully qualified class names unless import conflicts force it.
- Micronaut Java code uses JSpecify nullness annotations from `org.jspecify.annotations`; use JSpecify for new or modified nullability contracts.
- New Java packages must include `package-info.java` with `@NullMarked` and `import org.jspecify.annotations.NullMarked`; when touching an existing package without `@NullMarked`, add it unless the local code has an explicit exception.
- Use `org.jspecify.annotations.Nullable` for nullable values, including nullable parameters, return values, fields, array/component positions such as `String @Nullable []`, nullable collection elements such as `List<@Nullable T>`, and nullable type bounds such as `<T extends @Nullable Object>`.
- Preserve existing nullability intent when editing older code. Use JSpecify for new or modified contracts, but do not rewrite deliberate compatibility annotations such as `io.micronaut.core.annotation.Nullable` or `jakarta.annotation.Nullable` unless the task is specifically a nullability migration and compatibility impact has been checked.
- Avoid reflection-oriented implementations in framework code paths; prefer Micronaut compile-time/introspection mechanisms.
- Use `jakarta.inject` APIs for DI, not `javax.inject`.
- Prefer constructor injection and immutable state over field injection.
- For configuration models, prefer `@ConfigurationProperties` over scattered `@Value` usage.

### 3) Enforce API boundaries and compatibility

- Treat all public-facing changes through a Semantic Versioning lens (`https://semver.org/`) before implementation.
- Classify impact explicitly: patch for backward-compatible fixes, minor for backward-compatible feature additions, major for breaking API/behavioral changes.
- Keep public API binary compatible unless a major-version change explicitly allows breaks.
- Prefer non-breaking API evolution first: deprecate existing methods and add replacement variants/overloads instead of deleting methods or changing signatures in place.
- When using the deprecate-and-add path, keep deprecated APIs functional, point to replacements in Javadoc, and schedule removals only for the next major version.
- If breaking public-facing changes are explicitly allowed, document them in the user guide under `src/main/docs/guide` with migration notes, and update `toc.yml` when adding new guide sections.
- Mark non-user-facing APIs with `@io.micronaut.core.annotation.Internal`.
- Mark unstable public APIs with `@io.micronaut.core.annotation.Experimental` and avoid presenting them as stable contracts.
- Mark members directly called by generated code with `@io.micronaut.core.annotation.UsedByGeneratedCode`; preserve those signatures unless the generated-code callers are updated in the same change.
- Keep visibility as narrow as possible for non-public internals.
- When deprecating API, provide migration-friendly Javadoc and avoid silent behavioral breaks.

### 4) Document Java APIs and implementation intent

- All public Java types and public methods must have Javadoc. Include public constructors when they are part of the user-facing API.
- All new public Java types, methods, and user-facing constructors must include an `@since` Javadoc tag for the version where the PR will debut.
- Determine the `@since` version from the approved target branch, not from guesswork. Inspect that branch's `gradle.properties` (`projectVersion`) and use the corresponding release version; for example, `4.9.0-SNAPSHOT` means the new API debuts in `4.9.0`.
- If the PR is retargeted during follow-through, re-check the target branch's `gradle.properties` and update any newly added `@since` tags when the debut version changes.
- Do not add `@since` tags to internal-only APIs annotated with `@Internal` unless the repository already does so for that internal package.
- Internal and package-private methods should include maintainer-focused Javadoc when the implementation contract, lifecycle, invariants, or generated-code interaction is not obvious from the signature.
- Complex new code should include focused inline comments that explain implementation decisions or invariants; do not narrate straightforward control flow.

### 5) Keep Gradle/build changes convention-aligned

- Use `./gradlew` for all Gradle execution.
- Use Gradle version catalogs (`gradle/libs.versions.toml`) instead of hard-coded dependency versions.
- Use appropriate scopes (`api`, `implementation`, `compileOnly`, `runtimeOnly`) based on API exposure.
- Do not add custom build logic directly in module build files when it belongs in convention plugins.
- When uncertain about module paths, use `./gradlew projects` and prefer canonical `micronaut-*` project names.

### 6) Verify before completion

First confirm canonical verification tasks from `CONTRIBUTING.md` and existing CI/build files, then run the repository equivalents from root.

Common sequence in Micronaut repositories:

```bash
./gradlew :<module>:compileTestJava
# If module includes Groovy tests:
./gradlew :<module>:compileTestGroovy
./gradlew :<module>:test --tests 'pkg.ClassTest'
./gradlew :<module>:test
# If repository documents cM alias/checkstyle aggregate task:
./gradlew -q cM
./gradlew -q spotlessCheck
./gradlew check
./gradlew docs
```

For API-affecting changes, also run if configured in the repository:

```bash
./gradlew japiCmp
```

If Spotless fails, run `./gradlew -q spotlessApply` and re-run `spotlessCheck`.

## Guardrails

- Do not introduce `javax.inject` usage.
- Do not introduce legacy or third-party nullability annotations for new or modified Micronaut Java contracts when JSpecify is available, except for deliberate compatibility annotations whose impact has been checked.
- Do not hard-code dependency versions in module build files.
- Do not break public APIs without explicit major-version intent.
- Do not skip tests or docs verification for code changes.
- Do not use reflection as a convenience in framework internals.

## Delivery Contract

When finishing implementation work, report:

1. Exactly which files changed and why.
2. Whether the change is API-facing or internal-only.
3. Semantic Versioning impact classification (patch/minor/major) for any public-facing change.
4. For deprecate-and-add API evolution, which elements were deprecated and which replacement variants were introduced.
5. For breaking public-facing changes, which user guide files were updated and what migration guidance was added.
6. For new public Java APIs, the `@since` version used and how it was derived from the target branch's `gradle.properties`.
7. Commands executed for verification and outcomes.
8. Any follow-up risk (for example compatibility implications).

## Validation Checklist

- [ ] `SKILL.md` frontmatter is valid and `name` matches directory (`coding`).
- [ ] Guidance is maintainer-focused (not end-user app guidance).
- [ ] Java conventions include JSpecify nullability (`@NullMarked`, `@Nullable`), DI, and reflection-free guidance.
- [ ] API boundary guidance includes `@Internal`, `@Experimental`, `@UsedByGeneratedCode`, and compatibility checks.
- [ ] Public Java types and methods require Javadoc.
- [ ] New public Java APIs require `@since` tags derived from the approved target branch's `gradle.properties`.
- [ ] Internal/package-private methods and complex new code include maintainer-focused Javadoc or inline comments when implementation intent is not obvious.
- [ ] For public API evolution without breaking changes, deprecations include clear replacement guidance and functional compatibility is preserved.
- [ ] If breaking changes are allowed, user guide docs in `src/main/docs/guide` are updated with migration notes.
- [ ] Verification includes tests, style checks, `check`, and `docs`.

## References

- `CONTRIBUTING.md`
- `MAINTAINING.md`
- `.agents/skills/gradle/SKILL.md`
- `.agents/skills/docs/SKILL.md`
- `.agents/skills/skill-creator/references/spec-checklist.md`
