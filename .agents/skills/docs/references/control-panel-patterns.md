# Micronaut Control Panel Guide Patterns

This reference captures practical guide-authoring conventions observed in `micronaut-projects/micronaut-control-panel` and applicable to other Micronaut framework modules.

## Structure patterns

Source files:

- `src/main/docs/guide/toc.yml`
- `src/main/docs/guide/quickStart.adoc`
- `src/main/docs/guide/controlPanels.adoc`

Observed conventions:

1. `toc.yml` defines order and nesting.
2. Nested sections map to folders/files under `src/main/docs/guide/`.
3. Top-level section pages are concise and delegate details to subpages.

## `toc.yml` nested section pattern

Observed pattern:

```yaml
controlPanels:
  title: Available Control Panels
  builtIn: Built-in
  management: Management
```

Mapping:

- `src/main/docs/guide/controlPanels.adoc`
- `src/main/docs/guide/controlPanels/builtIn.adoc`
- `src/main/docs/guide/controlPanels/management.adoc`

## Content patterns in `.adoc`

### Dependency instructions

Use `dependency:` macro with scope where relevant:

```asciidoc
dependency:io.micronaut.controlpanel:micronaut-control-panel-ui[scope=developmentOnly]
```

### Configuration properties includes

Use generated include fragments:

```asciidoc
include::{includedir}configurationProperties/io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration.adoc[]
```

### Runtime environment commands

Use explicit environment examples in `[source,bash]` blocks:

```bash
MICRONAUT_ENVIRONMENTS=dev ./gradlew run
```

### Endpoint links and warnings

- Prefer links to official `docs.micronaut.io` endpoint sections.
- Use admonitions for critical requirements (`WARNING:`).

## Build workflow alignment

Contributing workflow in control-panel matches template repos:

- `./gradlew publishGuide` (or `./gradlew pG`) for guide output.
- `./gradlew docs` for guide + Javadocs.

## Example-source strategy

- Keep runnable examples in `doc-examples/` when the repository contains that project.
- Prefer including examples via Micronaut docs macros/snippet workflows so docs stay aligned with tested code.
- Avoid large hand-written code blocks in guide pages when the same content can be sourced from executable examples.

## Maintainer takeaways

1. Keep `toc.yml` and guide files synchronized at all times.
2. Favor macro/includes-backed content to reduce drift.
3. Keep examples runnable and environment-explicit.
4. Validate docs output before finalizing changes.
