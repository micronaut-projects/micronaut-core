# Guide Authoring Conventions

Micronaut Guides are generated tutorials. A single guide source can render several language and build-tool variants, so write source-backed content and avoid hard-coded generated paths where guide macros can do the work.

## Guide Directory

A regular guide normally lives at:

```text
guides/<slug>/
|-- metadata.json
|-- <slug>.adoc
|-- java/
|-- groovy/
|-- kotlin/
`-- src/main/resources/
```

Use one root AsciiDoc file named `<slug>.adoc` unless `metadata.json` sets `asciidoctor`.

For a single generated application, metadata should use an app named `default`. For multi-application guides, create a directory per app name and place each language tree inside that app directory:

```text
guides/<slug>/<app-name>/java/src/main/java/example/micronaut/...
guides/<slug>/<app-name>/kotlin/src/main/kotlin/example/micronaut/...
guides/<slug>/<app-name>/groovy/src/main/groovy/example/micronaut/...
```

## Metadata

Current upstream metadata is modeled by `buildSrc/src/main/java/io/micronaut/guides/core/Guide.java` and the generated schema at `buildSrc/src/main/resources/guide-metadata.schema.json`.

Required fields:

- `title`
- `intro`
- non-empty `authors`
- non-empty `categories`
- `publicationDate` in `YYYY-MM-DD`
- non-empty `apps`

Common optional fields:

- `minimumJavaVersion`
- `maximumJavaVersion`
- `cloud`
- `skipGradleTests`
- `skipMavenTests`
- `asciidoctor`
- `languages`
- `tags`
- `buildTools`
- `testFramework`
- `zipIncludes`
- `slug`
- `publish`
- `base`
- `env`

Use category labels from `Category.java`. At the inspected upstream revision, labels included Getting Started, Core Basics, Validation, Development, Testing, MCP, Object Storage, Email, Messaging, Logging, Scheduling, Cache, Patterns, i18n, Database Modeling, Data JDBC, Data JPA, Schema Migration, Data R2DBC, MongoDB, Data Access, Micronaut Security, Authorization Code, Client Credentials, Secrets Manager, HTTP Server, HTTP Client, Beyond JSON, JAX-RS, WebSockets, GraphQL, OpenAPI, JSON Schema, Distributed Tracing, Service Discovery, Distributed Configuration, Metrics, Distribution, Registry, GraalVM, Coordinated Restore at Checkpoint, Kubernetes, Serverless, AWS Lambda, Scale to Zero Containers, Views, Turbo, Static Resources, Kotlin, GraalPy, Spring Boot, and Boot to Micronaut Building a REST API. Refresh this list from current upstream before choosing a category.

## Starter Features and Apps

Guide `apps` features must be valid Micronaut Starter features. If a feature is not available from Starter, inspect existing custom features in `buildSrc/src/main/java/io/micronaut/guides/feature` and dependency versions in `buildSrc/src/main/resources/pom.xml`. Add custom features only when the guide topic requires them and validation covers the change.

Example single-app metadata shape:

```json
{
  "title": "Micronaut HTTP Client",
  "intro": "Learn how to use Micronaut HTTP Client.",
  "authors": ["Sergio del Amo"],
  "categories": ["HTTP Client"],
  "publicationDate": "2026-05-06",
  "apps": [
    {
      "name": "default",
      "features": ["http-client"]
    }
  ]
}
```

## AsciiDoc Placeholders

Guide source can use placeholders expanded by the build:

- `@language@`
- `@guideTitle@`
- `@guideIntro@`
- `@micronaut@`
- `@lang@`
- `@build@`
- `@testFramework@`
- `@authors@`
- `@languageextension@`
- `@testsuffix@`
- `@sourceDir@`
- `@minJdk@`
- `@api@`
- `@features@`
- `@features-words@`

## Common Snippets and Macros

Reusable snippets live under `src/docs/common`. Prefer existing snippets for headers, requirements, complete-solution, app creation, test, run, GraalVM, and next-step sections.

Common guide macros include:

- `common:<file>[]`: include a common snippet.
- `source:<ClassName>[]`: include a source file from the current language's main source directory.
- `resource:<file>[]`: include a resource from `src/main/resources`.
- `test:<ClassName>[]`: include a test file and account for JUnit, Kotest, or Spock naming.
- `testResource:<file>[]`: include a test resource.
- `callout:<name>[]`: include a common callout snippet.
- `external:<path>[]`: include content from a base or external guide.
- `dependency:<artifact>[groupId=...,scope=...,version=...]`: render dependency lines.
- `guideLink:<slug>[...]`: link to another guide using guide-site paths.

Most macros accept options such as `tags`, `lines`, `indent`, and `app`. Inspect nearby guides and buildSrc macro tests before using less common options.

## Conditional Blocks

Use custom exclusion blocks when a paragraph or instruction applies only to some render variants:

```asciidoc
:exclude-for-languages:kotlin
This text renders for Java and Groovy, not Kotlin.
:exclude-for-languages:

:exclude-for-build:maven
Run `./gradlew run`.
:exclude-for-build:
```

Also inspect current support for JDK-based exclusions before writing Java-version-specific content.

## Base and Partial Guides

`publish` defaults to true. Set `publish: false` only for draft, base, or partial guides that should not appear as public guide topics.

Use `base` when a guide extends reusable source or narrative from another guide. Inspect the base guide carefully; it may provide shared app code, common AsciiDoc, or resource files. Do not count `publish: false` guide directories as public topic coverage during dedupe, but do use them to avoid duplicating shared scaffolding.

## Code Quality

- Keep application code runnable and focused on the guide's teaching goal.
- Put sample code under `example.micronaut` unless nearby guides for the topic use another package.
- Prefer tests that prove the user's expected outcome.
- Use snippet tags when only part of a file should appear in the guide.
- Avoid prose that drifts from the code; include actual source, resources, and tests with guide macros.
- For cloud and security guides, use placeholders for secrets and include cleanup instructions.
