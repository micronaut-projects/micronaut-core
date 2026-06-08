# Micronaut Docs Providers and Guide Macros

This reference captures source-backed behavior relevant to framework maintainers writing guide documentation.

## 1) Configuration property writer from `micronaut-docs`

`micronaut-docs` provides a metadata writer that emits AsciiDoc configuration property fragments.

Source evidence:

- `docs-asciidoc-config-props/src/main/java/io/micronaut/documentation/asciidoc/AsciiDocPropertyReferenceWriter.java`
- `docs-asciidoc-config-props/src/main/resources/META-INF/services/io.micronaut.inject.configuration.ConfigurationMetadataWriter`

Behavior:

- Writes `META-INF/config-properties.adoc` from Micronaut configuration metadata.
- Produces property table content (property, type, description, default value).

Maintainer implication:

- Prefer `include::{includedir}configurationProperties/<fqcn>.adoc[]` in guides instead of manually writing property tables.

## 2) Asciidoctor extension registry used in module docs builds

Guide macros used by Micronaut module docs are registered by `micronaut-build`.

Source evidence:

- `micronaut-build/micronaut-gradle-plugins/src/main/groovy/io/micronaut/docs/DocsExtensionRegistry.groovy`
- `micronaut-build/micronaut-gradle-plugins/src/main/resources/META-INF/services/org.asciidoctor.jruby.extension.spi.ExtensionRegistry`

Registered extensions include:

- Inline macros: `api`, `mnapi`, `ann`, `pkg`, `jdk`, `jee`, `rs`, `rx`, `reactor`, `dependency`
- Block macro: `snippet`
- Block processor: `configuration`

## 3) `dependency:` macro

Source:

- `micronaut-build/micronaut-gradle-plugins/src/main/groovy/io/micronaut/docs/BuildDependencyMacro.groovy`

Usage:

- `dependency:io.micronaut.controlpanel:micronaut-control-panel-ui[scope=developmentOnly]`

Why maintainers should prefer it:

- Keeps dependency snippets consistent across Gradle/Maven output.
- Avoids drift from hand-written dual build-tool snippets.

## 4) `snippet::` macro

Source:

- `micronaut-build/micronaut-gradle-plugins/src/main/groovy/io/micronaut/docs/LanguageSnippetMacro.groovy`

Maintainer guidance:

- Use for examples that should stay aligned with source/test files.
- Prefer tagged snippets for stable, focused excerpts.

## 5) `[configuration]` block processor

Source:

- `micronaut-build/micronaut-gradle-plugins/src/main/groovy/io/micronaut/docs/ConfigurationPropertiesMacro.groovy`

Maintainer guidance:

- Use for multi-format configuration examples rather than one-off single-format blocks.

## 6) Docs task wiring in `micronaut-build`

Source:

- `micronaut-build/micronaut-gradle-plugins/src/main/groovy/io/micronaut/build/MicronautDocsPlugin.groovy`

Relevant properties and behavior:

- Guide tasks include `publishGuide*`, `assembleDocs`, and `docs`.
- Asciidoctor properties include `includedir`, `micronautapi`, `source-highlighter`, and language handling.
- Built docs are assembled into `build/docs/`.

## Practical rules

1. Prefer macro-driven docs (`dependency:`, `snippet::`, `[configuration]`) over hand-maintained formatting.
2. Prefer generated config property includes over manually authored property tables.
3. Validate with `./gradlew publishGuide` and `./gradlew docs` before completion.
