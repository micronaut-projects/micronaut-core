# Scala Docs Snippet Parity Catalog And Burn-Down

## Summary

Build a durable Scala documentation parity catalog for every `snippet::` reference under `src/main/docs`, then add Scala equivalents and JUnit-backed tests in `test-suite-scala` for all portable examples.

The read-only baseline from the initial scan was:

- 335 snippet references
- 349 expanded target inclusions
- 266 unique targets
- 82 existing Scala target inclusions
- 267 missing Scala inclusions
- 19 existing Scala inclusions with missing tags

Before starting implementation, rerun the scanner and treat the fresh result as the source of truth.

## Durable Artifacts

- `test-suite-scala/DOCS_SNIPPET_PARITY_PLAN.md`: implementation plan and reference workflow.
- `test-suite-scala/DISABLED_TESTS.md`: canonical docs snippet parity catalog and burn-down backlog.
- `test-suite-scala/src/test/scala/io/micronaut/docs/...`: executable Scala snippet sources and owning specs.

## Catalog Requirements

Extend `test-suite-scala/DISABLED_TESTS.md` into the canonical docs snippet parity catalog.

Catalog every expanded snippet target with:

- source `.adoc`
- target class or file
- referenced tags
- expected Scala path
- status
- owning test
- reason

Use these statuses:

- `covered`: Scala source exists, referenced tags exist, and owning tests pass.
- `missing`: portable target has no Scala equivalent yet.
- `pending-runtime`: Scala source compiles, but the behavior test is disabled because runtime support is not ready.
- `pending-compile`: portable target cannot be checked into the compiled Scala source set yet.
- `unsupported`: source is not portable to Scala.
- `scala-specific`: Scala-only companion coverage that is not a direct port of a docs snippet.

Classify non-portable snippets explicitly instead of silently dropping them. Known examples include Kotlin suspend/coroutines, Java `package-info`, Java records, and language-specific APIs without a Scala equivalent.

## Implementation Rules

- Generate or port Scala sources under `test-suite-scala/src/test/scala/io/micronaut/docs/...` for every portable snippet target.
- Preserve every tag referenced by docs snippets.
- Add JUnit 5 tests for each portable behavior group.
- Supporting snippet-only files must compile.
- Behavior snippets get an owning `*Spec.scala`.
- Mark failing runtime tests with `@Disabled("pending: <catalog id/reason>")` and record the same item as `pending-runtime` in the catalog.
- Do not check uncompilable Scala snippets into the compiled source set.
- Track compile blockers as `pending-compile` catalog entries until the Scala adapter or example can support them.
- Keep snippets idiomatic Scala: traits, constructor params, case classes, `val`/`var`, `Option`, Scala collections, and explicit nulls where relevant.
- Avoid shared runtime changes unless a failing docs test proves they are necessary.
- Any shared-core fix must include Java, Groovy, and Kotlin guardrail tests.

## Implementation Order

### Wave 1: Catalog Generation

Generate the catalog from all `src/main/docs/**/*.adoc` `snippet::` references.

Update `test-suite-scala/DISABLED_TESTS.md` with current coverage, missing files, missing tags, and unsupported classifications.

Validation target: every expanded snippet target has exactly one catalog entry.

### Wave 2: Existing Scala Tag Repair

Fill missing tags in existing Scala docs sources first.

This is low risk because the source files already exist and the work mainly improves current docs coverage.

### Wave 3: Portable Example Ports

Port missing portable examples in section order:

1. IOC, configuration, and AOP
2. HTTP server and client
3. Management and endpoints
4. Context propagation
5. Validation and binding
6. Remaining utilities

### Wave 4: JUnit Backing Tests

Generate or port JUnit tests for each example group.

Run focused specs as each group is added, then run the full Scala docs suite.

Runtime failures should become disabled tests with matching `pending-runtime` catalog entries.

Compile blockers should remain out of the compiled source set and become `pending-compile` catalog entries.

### Wave 5: Pending Burn-Down

Iterate on pending items in focused commits.

Remove `@Disabled` annotations as each runtime case passes, and update `test-suite-scala/DISABLED_TESTS.md` in the same change.

Burn down pending failures by cluster, with one root-cause fix and one verification loop per cluster:

1. `scala-docs-041` through `scala-docs-043`: `config.builder`
   - Reproduce by temporarily enabling `test-suite-scala/src/test/scala/io/micronaut/docs/config/builder/VehicleSpec.scala`.
   - Fix `@ConfigurationBuilder` population for Scala builder-style properties.
   - Done when `EngineConfig`, `EngineFactory`, and `VehicleSpec` move from `pending-runtime` to `covered`.
2. `scala-docs-061`: `config.itfce`
   - Reproduce by temporarily enabling `test-suite-scala/src/test/scala/io/micronaut/docs/config/itfce/VehicleSpec.scala`.
   - Fix trait-based `@ConfigurationProperties` nested configuration introduction advice.
   - Done when `EngineConfig` moves from `pending-runtime` to `covered`.
3. `scala-docs-219` through `scala-docs-221`: websocket client/server examples
   - Reproduce by temporarily enabling `test-suite-scala/src/test/scala/io/micronaut/docs/http/server/netty/websocket/WebSocketSpec.scala`.
   - Fix Scala `@ClientWebSocket` introduction generation for generated `setWebSocketSession`.
   - Done when the websocket snippets move from `pending-runtime` to `covered`.
4. `scala-docs-060`: `expressions.AnnotationContextExample`
   - Keep uncompilable source out of `src/test/scala` until the Scala annotation processor can expose member-scoped `@AnnotationExpressionContext` metadata.
   - Add a minimal compile guard before moving the snippet into the compiled source set.
   - Done when the catalog entry moves from `pending-compile` to `covered`.
5. `scala-docs-193`: `server.routing.RouteConditionController`
   - Keep uncompilable source out of `src/test/scala` until Scala route-condition expressions can compile request evaluation variables.
   - Add a minimal compile guard before moving the snippet into the compiled source set.
   - Done when the catalog entry moves from `pending-compile` to `covered`.

For each pending cluster:

- Start from the disabled spec or absent snippet recorded in `test-suite-scala/DISABLED_TESTS.md`.
- Make the smallest Scala adapter or shared-core change that proves the docs behavior.
- Add Java, Groovy, and Kotlin guardrail tests if the fix changes shared Micronaut behavior.
- Remove the local `@Disabled("pending: ...")` only after the focused spec passes.
- Update the catalog entry to `covered`, including the owning test and the verification command.
- Rerun the catalog scanner so every expanded snippet target still has exactly one catalog entry.

### Wave 6: Published Guide Rendering

Scala parity is not complete until Scala snippets render in the generated guide HTML. Do not hand-edit `build/docs/index.html` or `build/docs/guide/index.html`; they are generated artifacts.

The guide rendering path has its own requirements:

- The `snippet::` macro implementation consumed by Core must include Scala in the default language set.
- `test-suite-scala` must be the default Scala snippet project.
- `language="scala"` and `languages="scala"` filters must be supported.
- The generated multi-language selector JavaScript must treat `scala` as a supported language.
- The multi-language selector CSS must include Scala selectors so Scala tabs are usable and visually consistent.

Known build wiring caveat:

- `./gradlew --include-build ../build docs` includes the Micronaut Build checkout for the root Core build, but it does not automatically substitute `io.micronaut.build.internal:micronaut-gradle-plugins` inside Core's separate `buildSrc` build.
- Prove the active docs macro with:

```bash
./gradlew -p buildSrc dependencyInsight --dependency micronaut-gradle-plugins --configuration runtimeClasspath
```

- A Scala-capable local Micronaut Build checkout can be proven for `buildSrc` with an init script that includes the sibling build only when `settings.rootProject.name == "buildSrc"`. This is a verification tool, not a substitute for a released plugin version.
- The durable fix is to consume a Micronaut Build plugin version that contains the Scala-capable `LanguageSnippetMacro` and matching JS/CSS resources, or to carry an explicit, documented composite-build mechanism while this branch is in development.

Guide rendering validation:

```bash
./gradlew -p buildSrc dependencyInsight --dependency micronaut-gradle-plugins --configuration runtimeClasspath
./gradlew publishGuide --rerun-tasks
rg -n 'data-lang="scala"|language-scala' build/working/02-docs-raw/all/index.html build/docs/guide/index.html
./gradlew docs
```

Inspect at least one normal multi-language snippet group, such as the Quick Start controller/client snippets, and verify Scala appears beside Java, Groovy, and Kotlin.

## Test Plan

Run these checks as the work progresses:

```bash
python3 test-suite-scala/scripts/scala_docs_snippet_catalog.py --check
./gradlew :test-suite-scala:test
./gradlew --include-build ../build docs
./gradlew docs
```

When production Scala adapter changes are needed, also run:

```bash
./gradlew :micronaut-inject-scala-test:test --tests '*Scala*'
```

When shared-core behavior changes are needed, run the closest Java, Groovy, and Kotlin source specs matching the changed behavior.

## Assumptions

- "All snippets" means every `snippet::` macro in `src/main/docs`, expanded per target class or file.
- Portable snippets get Scala equivalents and tests.
- Non-portable snippets are cataloged as unsupported or deferred with reasons.
- Pending runtime cases use JUnit 5 `@Disabled` plus a matching catalog entry.
- `test-suite-scala/DISABLED_TESTS.md` remains the durable catalog/backlog artifact.
