# Micronaut Build Plugin Map (Source-Backed)

This reference is for Micronaut Framework committers. It summarizes plugin behavior from `micronaut-projects/micronaut-build` source (branch `8.0.x`), not from README prose.

## Plugin Registration and IDs

In `micronaut-build`, plugin IDs are generated via internal helper logic:

- `definePlugin("<name>", ...)` -> `io.micronaut.build.internal.<name>`
- Exception: shared settings plugin is explicitly `io.micronaut.build.shared.settings`

Primary registration files:

- `micronaut-gradle-plugins/build.gradle.kts`
- `micronaut-kotlin-build-plugins/build.gradle.kts`

## Canonical Template Entry Points

For repositories generated from the template, default entry points are:

- `settings.gradle(.kts)`: `io.micronaut.build.shared.settings`
- root `build.gradle(.kts)`: `io.micronaut.build.internal.parent`
- `buildSrc` convention plugin: wraps module-level internal plugins as needed

## Project Plugins (internal IDs)

- `io.micronaut.build.internal.aot-module` -> `io.micronaut.build.aot.MicronautAotModulePlugin`
- `io.micronaut.build.internal.base` -> `io.micronaut.build.MicronautBasePlugin`
- `io.micronaut.build.internal.base-module` -> `io.micronaut.build.MicronautBaseModulePlugin`
- `io.micronaut.build.internal.binary-compatibility-check` -> `io.micronaut.build.compat.MicronautBinaryCompatibilityPlugin`
- `io.micronaut.build.internal.bom` -> `io.micronaut.build.MicronautBomPlugin`
- `io.micronaut.build.internal.common` -> `io.micronaut.build.MicronautBuildCommonPlugin`
- `io.micronaut.build.internal.dependency-updates` -> `io.micronaut.build.MicronautDependencyUpdatesPlugin`
- `io.micronaut.build.internal.docs` -> `io.micronaut.build.MicronautDocsPlugin`
- `io.micronaut.build.internal.java-base` -> `io.micronaut.build.MicronautBuildJavaBasePlugin`
- `io.micronaut.build.internal.kotlin-base` -> `io.micronaut.build.MicronautBuildKotlinBasePlugin`
- `io.micronaut.build.internal.module` -> `io.micronaut.build.MicronautModulePlugin`
- `io.micronaut.build.internal.parent` -> `io.micronaut.build.MicronautParentPlugin`
- `io.micronaut.build.internal.parent-publishing` -> `io.micronaut.build.MicronautParentPublishingPlugin`
- `io.micronaut.build.internal.publishing` -> `io.micronaut.build.MicronautPublishingPlugin`
- `io.micronaut.build.internal.quality-checks` -> `io.micronaut.build.MicronautQualityChecksParticipantPlugin`
- `io.micronaut.build.internal.quality-reporting` -> `io.micronaut.build.MicronautQualityReportingAggregatorPlugin`
- `io.micronaut.build.internal.version-catalog-updates` -> `io.micronaut.build.catalogs.MicronautVersionCatalogUpdatePlugin`

Kotlin-specific plugin IDs:

- `io.micronaut.build.internal.kotlin` -> `io.micronaut.build.kotlin.MicronautBuildKotlinPlugin`
- `io.micronaut.build.internal.kotlin-kapt` -> `io.micronaut.build.kotlin.MicronautBuildKotlinKaptPlugin`
- `io.micronaut.build.internal.kotlin-ksp` -> `io.micronaut.build.kotlin.MicronautBuildKotlinKspPlugin`

Settings plugin IDs:

- `io.micronaut.build.internal.develocity` -> `io.micronaut.build.MicronautDevelocityPlugin` (apply in `settings.gradle(.kts)`)
- `io.micronaut.build.shared.settings` -> `io.micronaut.build.MicronautSharedSettingsPlugin`

## High-Impact Behaviors by Plugin

### `base` (`MicronautBasePlugin`)

- Applies dependency resolution configuration and build extension creation.
- Validates `projectVersion` format (must start with a digit) and sets `project.version`.
- Applies configuration properties, aggregated Javadoc participant, and native image support helpers.

### `common` (`MicronautBuildCommonPlugin`)

- Applies `base` and quality checks participant.
- Configures Java/Groovy conventions, Spotless, Test Logger, IDEA defaults, JAR manifest attributes.
- Adds Micronaut BOM as platform when enabled.
- Enforces Micronaut core major/minor version consistency after resolution unless disabled intentionally.
- Registers `allDeps` task.

### `java-base` (`MicronautBuildJavaBasePlugin`)

- Applies Java plugin and aligns compile/test Java behavior from `micronautBuild` extension.
- Supports toolchains or source/target compatibility fallback.
- Configures test defaults (JUnit platform, locale normalization, Develocity test retry/PTS integration when present).

### `kotlin-base` and Kotlin wrappers

- `kotlin-base` bridges Kotlin JVM target to `micronautBuild.javaVersion` using reflection.
- `kotlin` applies Kotlin JVM + `kotlin-base`.
- `kotlin-kapt` and `kotlin-ksp` layer annotation processing plugin choice on top.

### `base-module` and `module`

- `base-module` composes common, dependency-updates, publishing, binary compatibility, Sonatype configuration, and module descriptor generation.
- Asserts expected settings plugin state through shared service registration.
- Registers POM checking once `maven-publish` is present.
- `module` adds Micronaut injection/doc processors and framework-specific test framework dependencies.

### `bom` (`MicronautBomPlugin`)

- Configures Java Platform + Version Catalog + publishing + binary compatibility.
- Creates `micronautBom` extension with inlining, inclusion, and exclusion controls.
- Generates and mutates BOM/catalog outputs, supports nested catalog/BOM inlining and version inference.
- Maintains helper configurations for BOM and catalog resolution/inference.

### `dependency-updates` (`MicronautDependencyUpdatesPlugin`)

- If root `gradle/libs.versions.toml` exists, applies catalog update plugin on root and exits.
- Otherwise, configures legacy resolution strategy and registers deprecated compatibility tasks named `dependencyUpdates` and `useLatestVersions`.

### `version-catalog-updates` (`MicronautVersionCatalogUpdatePlugin`)

- Root-only plugin.
- Registers `updateVersionCatalogs`, `useLatestVersions` (real catalog copy task), and compatibility `dependencyUpdates` task.
- Defaults: reject prerelease qualifiers, allow minor updates, disallow major updates.

### `publishing` (`MicronautPublishingPlugin`)

- Applies `maven-publish`; conditionally applies signing when credentials and keys are present.
- Populates POM metadata from project/root properties.
- Configures local build repo and optional external publishing endpoint.
- Embeds generated POM into JAR artifacts for scanner compatibility.

### `parent` and `parent-publishing`

- `parent` is root-oriented composition (`docs`, `quality-reporting`, `parent-publishing`).
- `parent-publishing` is root-only and non-snapshot focused; prepares Maven Central bundle and publish task.

### `quality-checks` and `quality-reporting`

- Participant plugin configures Checkstyle, Sonar integration hooks, and JaCoCo enablement behavior.
- Aggregator plugin is root-only; configures Sonar + Jacoco report aggregation and links subproject quality data.

### `binary-compatibility-check`

- Configures baseline discovery and `japicmp` checks for Java modules.
- Supports version-catalog compatibility checks for BOM modules.
- Wires into `check` and honors accepted regressions configuration files.

### `docs` (`MicronautDocsPlugin`)

- Creates end-to-end docs pipeline: resources, guide generation, property reference generation, assembly, validation, release dropdown, zip outputs.
- Integrates with Javadoc aggregation and GitHub tag data service.

### `aot-module`

- Composes `base-module`, disables standard DI/BOM processing defaults for AOT module semantics.
- Adds AOT core/context dependencies and test fixture capability wiring.

### Shared settings plugin (`io.micronaut.build.shared.settings`)

- Applies build settings + Develocity behavior.
- Configures project naming standardization, root version/group propagation, optional Nexus publishing setup.
- Provides settings extension APIs for importing Micronaut catalogs and including development versions from upstream repos.
- Applies `MicronautDevelocityPlugin` as part of shared settings wiring.
- Writes `micronaut-build-version` into `buildSrc/gradle.properties` when `buildSrc` exists.

## Extension Contracts to Know

### Project extension: `micronautBuild`

Important properties in `MicronautBuildExtension` include:

- `javaVersion`, `testJavaVersion`
- `checkstyleVersion`
- `dependencyUpdatesPattern`
- `enforcedPlatform` (guarded in common plugin behavior)
- `enableProcessing`, `enableBom`
- `testFramework`
- `useToolchains`
- `bomSuppressions` and compile option hooks

### Settings extension: `micronautBuild`

`MicronautBuildSettingsExtension` includes:

- cache toggles (`useLocalCache`, `useRemoteCache`)
- project naming standardization controls
- catalog import helpers (`importMicronautCatalog*`)
- development-version include helper (`requiresDevelopmentVersion`)

## Committer Triage Shortcuts

- Micronaut version mismatch errors: inspect `compileClasspath`/`runtimeClasspath` and use `dependencyInsight`.
- Version catalog update behavior: confirm root plugin application and `gradle/libs.versions.toml` presence.
- Release issues: check signing credentials, key presence, non-snapshot constraints, and root-only publishing tasks.
- Compatibility failures: validate baseline resolution and accepted API changes config before suppressing.

## Source Verification Pointers

Key source files to read in `micronaut-build`:

- Plugin registration:
  - `micronaut-gradle-plugins/build.gradle.kts`
  - `micronaut-kotlin-build-plugins/build.gradle.kts`
- Core plugins:
  - `MicronautBasePlugin.groovy`
  - `MicronautBuildCommonPlugin.groovy`
  - `MicronautBuildJavaBasePlugin.java`
  - `MicronautBuildKotlinBasePlugin.java`
  - `MicronautModulePlugin.groovy`
  - `MicronautBomPlugin.java`
  - `MicronautPublishingPlugin.java`
  - `MicronautDependencyResolutionConfigurationPlugin.java`
  - `MicronautSharedSettingsPlugin.java`
