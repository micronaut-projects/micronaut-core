---
description: Micronaut Development Guide
author: Cédric Champeau
version: 1.0
globs: ["**/*.java", "**/*.kts", "**/*.xml"]
---

# Micronaut Project Development Guide

## Project Overview

This project is a Micronaut module which is part of the Micronaut Framework, a modern, JVM-based, full-stack framework for building modular, easily testable microservice and serverless applications.

The description of what this project is doing can be found in the `gradle.properties` file under the `projectDesc` key.

This repository does not represent an actual application; its modules are designed to be used as dependencies by applications.
The root project MUST NOT contain any code: it is a parent project which coordinates the build and supplies documentation.

⚠️ CRITICAL: DO NOT USE attempt_completion BEFORE TESTING ⚠️

## Command Conventions

- You MUST run all Gradle commands (except testing ones) with the quiet option to reduce output:
    - Do NOT use: `--stacktrace`, `--info` (excessive output will not fit the context)
- You MUST run all Gradle testing commands without the `-q` option, since that will suppress the output result of each test.
- Gradle project names are prefixed with `micronaut-`. For example, directory `mylib` maps to Gradle project `:micronaut-mylib`.
- You MUST run Gradle with the Gradle wrapper: `./gradlew`.
- Gradle project names prefixed with `test-` or `test-suite` are not intended for users: they are functional tests of the project

## Main Development Tasks:

- Compile (module): `./gradlew -q :<module>:compileTestJava`
- Test (module): `./gradlew :<module>:test`
- Checkstyle (aggregate task): `./gradlew -q cM`
    - Note: `cM` is the canonical Checkstyle runner defined in build logic to validate Checkstyle across the codebase.
    - Note: you MUST NOT introduce new warnings. You SHOULD fix warnings in code that you have modified.
- Spotless (check license headers/format): `./gradlew -q spotlessCheck`
- Spotless (auto-fix formatting/headers): `./gradlew -q spotlessApply` (MUST be used to fix violations found by `spotlessCheck`)

## Code style

You SHOULD prefer modern Java idioms: records, pattern matching, sealed interfaces/classes, `var` for local variables.
You MUST NOT use fully qualified class names unless there is a conflict between 2 class names in different packages.
You MUST annotate the code with nullability annotations (`io.micronaut.core.annotation.Nullable`, `io.micronaut.core.annotation.NonNull`).
You MUST NOT use reflection: Micronaut is a reflection-free framework tailored for integration with GraalVM.
You MUST use `jakarta.inject` for dependency injection, NOT `javax.inject`.

## Binary compatibility

Micronaut projects are intended to be used in consumer applications and therefore follow semantic versioning. As a consequence:
- You MUST NOT break any public facing API without explicit consent
- You SHOULD run the `./gradlew japiCmp` task to get a report about binary breaking changes
- You SHOULD reduce the visibility of members for non user-facing APIs.
- You MUST annotate non-user facing APIs with `@io.micronaut.core.annotation.Internal`

## Implementation Workflow (Required Checklist)

You MUST follow this sequence after editing source files:

1) Compile affected modules
    - `./gradlew -q :<module>:compileTestJava :<module>:compileTestGroovy`

2) Run targeted tests first (fast feedback)
    - `./gradlew :<module>:test --tests 'pkg.ClassTest'`
    - `./gradlew :<module>:test --tests 'pkg.ClassTest.method'` (optional)

3) Run full tests for all affected modules
    - `./gradlew :<module>:test`

4) Static checks
    - Checkstyle: `./gradlew -q cM`

5) (Optional) If, and only if you have created new files, you SHOULD run
  - Spotless check: `./gradlew -q spotlessCheck`
  - If Spotless fails: `./gradlew -q spotlessApply` then re-run `spotlessCheck`
  - You MUST NOT add new license headers on existing files: only focus on files you have added

6) Verify a clean working tree
    - You SHOULD ensure no unrelated changes are pending before proposing changes.
    - Use `git_status` to verify the working tree:
      ```xml
      <use_mcp_tool>
       <server_name>mcp-server-git</server_name>
       <tool_name>git_status</tool_name>
       <arguments>
           {
           "repo_path": "/home/cchampeau/DEV/PROJECTS/micronaut/micronaut-langchain4j" // adjust absolute path if necessary
           }
       </arguments>
      </use_mcp_tool>
      ```

## Documentation Requirements

- You MUST update documentation when necessary, following the project’s documentation rules in `.clinerules/docs.md`.
- Before writing code, you SHOULD analyze relevant code files to get full context, then implement changes with minimal surface area.
- You SHOULD list assumptions and uncertainties that need clarification before completing a task.
- You SHOULD check project configuration/build files before proposing structural or dependency changes.

## Context7 Usage (Documentation and Examples)

You MUST use Context7 to get up-to-date, version-specific documentation and code examples for frameworks and libraries.

Preferred library IDs:
- Micronaut main docs: `/websites/micronaut_io`
- Micronaut Test: `/websites/micronaut-projects_github_io_micronaut-test`
- Micronaut Oracle Cloud: `/websites/micronaut-projects_github_io_micronaut-oracle-cloud`
- OpenRewrite: `/openrewrite/rewrite-docs`

Example (fetch docs for a topic):
```xml
<use_mcp_tool>
    <server_name>context7-mcp</server_name>
    <tool_name>get-library-docs</tool_name>
    <arguments>
        {
            "context7CompatibleLibraryID": "/openrewrite/rewrite-docs",
            "topic": "JavaIsoVisitor"
        }
    </arguments>
</use_mcp_tool>
```

For other libraries, you MUST resolve the library ID first:
```xml
<use_mcp_tool>
    <server_name>context7-mcp</server_name>
    <tool_name>resolve-library-id</tool_name>
    <arguments>
        {
            "libraryName": "Mockito"
        }
    </arguments>
</use_mcp_tool>
```

## Dependency Management (Version Catalogs)

- Main dependencies are managed in the Gradle version catalog at `gradle/libs.versions.toml`.
- You MUST use catalogs when adding dependencies (avoid hard-coded coordinates/versions in module builds).

Adding a new dependency (steps):
1) Choose or add the version in the appropriate catalog (`libs.versions.toml`).
2) Add an alias under the relevant section (e.g., `libraries`).
3) Reference the alias from a module’s `build.gradle.kts`, for example:
    - `implementation(libs.some.library)`
    - `testImplementation(testlibs.some.junit)`
4) Do NOT hardcode versions in module build files; use the catalog entries.

You SHOULD choose the appropriate scope depending on the use of the library:
    - `api` for dependencies which appear in public signatures or the API of a module
    - `implementation` for dependencies which are implementation details, only used in the method bodies for example
    - `compileOnly` for dependencies which are only required at build time but not at runtime
    - `runtimeOnly` for dependencies which are only required at run time and not at compile time

## Build logic

Micronaut projects follow Gradle best practices, in particular usage of convention plugins.
Convention plugins live under the `buildSrc` directory.

You MUST NOT add custom build logic directly in `build.gradle(.kts)` files.
You MUST implement build logic as part of convention plugins.
You SHOULD avoid build logic code duplication by moving common build logic into custom convention plugins.
You SHOULD try to prefer composition of convention plugins.

## Key Requirements

You MUST confirm all of the following BEFORE using `attempt_completion`:

- Changes compile successfully (affected modules)
- Targeted tests pass
- Full tests for affected modules pass
- Checkstyle (`cM`) passes
- Spotless (`spotlessCheck`) passes (apply fixes if needed)
- Documentation updated when necessary
- Working tree is clean (no unrelated diffs)

If ANY item is “no”, you MUST NOT use `attempt_completion`.
While you SHOULD add new files using `git add`, you MUST NOT commit (`git commit`) files yourself.
