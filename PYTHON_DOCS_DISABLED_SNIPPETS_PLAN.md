# Plan: Enable Disabled Python Documentation Snippets

## Summary
- Treat `DISABLED_TESTS.md` as the live bug backlog, but first reconcile it with actual `@Disabled` usage. `sse/HeadlineControllerSpec.py` is currently disabled in the tree and missing from the inventory.
- Fix one root cause at a time, with a focused `inject-python-test` or `context-python` regression before re-enabling the docs snippet.
- Do not add docs-side annotation shims, Java-style getters/setters, or package-info equivalents. Keep Python snippets idiomatic and re-enable them only after the compiler/runtime supports them.
- Whenever a Python docs test uses `ApplicationContext.run()`, convert it to `@MicronautTest` with injected beans to avoid GraalPy lifecycle issues, unless the snippet specifically documents manual context creation. Do this as the default first fix every time `ApplicationContext.run()` appears in a docs test; manual nested contexts can close the GraalPy context while generated Python test instances or async callbacks are still active.

## Important API/Compiler Changes
- Add an `inject-python` import convention for Java package segments that are Python keywords: allow trailing underscore imports such as `from micronaut.core.async_.annotation import SingleResult`, normalize them to `io.micronaut.core.async.annotation`, and keep decorator generation in `micronaut_transformer.py`.
- Preserve Java interface generic type arguments in `PythonClassElement.getInterfaces()` and related assignability/type-resolution paths.
- Fix Python annotation metadata so stereotypes, repeatable metadata, keyword members, nested annotation members, and `@NonBinding` members behave like Java metadata.
- Add internal `context-python`/generated-stub conversion support so Python callers see Python dataclass/value objects, while Micronaut internals can still use generated Java wrappers.

## Iterative Process
1. Reconcile inventory:
   - Generate actual disabled list with `rg -n "@Disabled\\(" test-suite-python/src/test/python/micronaut/docs`.
   - Add missing inventory rows, including `sse/HeadlineControllerSpec.py`.
   - Add or maintain columns for root cause, subsystem, focused regression, current status, and last failure summary.
2. For each root-cause group:
   - Temporarily enable one representative docs test.
   - Run the focused docs test and capture the exact failure.
   - Add the smallest core regression in `:micronaut-inject-python-test:test` or `:micronaut-context-python:test`.
   - Implement the compiler/runtime fix.
   - Run the core regression, the focused docs test, then the full Python docs suite.
   - Remove `@Disabled`, uncomment matching snippet TODOs, and update `DISABLED_TESTS.md`.
3. If a test exposes a separate failure after the first fix, re-disable only the failing method/class with the new reason and add it as a new backlog row.

## Fix Waves
- Wave 0: inventory and baseline.
  - Baseline command: `./gradlew --no-daemon -Dorg.gradle.java.home=/Users/graemerocher/.sdkman/candidates/java/current :test-suite-python:test --max-workers=2`.
  - Confirmed starting point: `180 tests executed, 78 skipped`.

- Wave 1: compiler metadata foundation.
  - Fix keyword-safe imports for `SingleResult` and `ReactorPropagation`; re-enable all commented `@SingleResult` and propagation snippets using `async_`.
  - Fix generic Java interface metadata for `Publisher`, `ApplicationEventListener`, `BeanCreatedEventListener`, `TypeConverter`, `RetryOperations`, `Writable`, `Mapper`, and qualifier use cases.
  - Fix annotation stereotype/member handling for `@Status`, `@Valid`, `@FilterMatcher`, `@RouteCondition`, custom binders, `@NonBinding`, management endpoints, and validation groups.
  - Unlocks: retry/streaming/SSE/client publisher tests, event listener tests, `@Any` qualifier tests, annotation-member qualifier test, filter matching, status tests, validation tests, and many commented listener/client snippets.

- Wave 2: wrapper leakage and Python-facing conversion.
  - Add a single internal conversion path for generated Java wrappers to return Python values at Python-facing boundaries: HTTP client bodies, error bodies, binders, `BeanIntrospection.instantiate`, collections, optionals, and publisher elements.
  - Keep Java-side Micronaut internals using wrappers where required.
  - Unlocks: `PetControllerSpec`, `HeaderSpec`, `HelloController` POJO tests, `httpclientexceptionbody`, `ioc/introspection/PersonSpec`, typed client binder tests, and related client/declarative-client failures.

- Wave 3: HTTP binding, validation, and response semantics.
  - Ensure generated method/argument metadata is copied to stubs so `@Status`, `@Valid`, validation groups, nullable headers, URI defaults, route conditions, and endpoint routing are visible to Micronaut.
  - Fix Python return adaptation for `None`, `Mono.empty`, and empty publishers to match Java 404 behavior.
  - Fix HTTP body conversion for `InputStream`, multipart byte arrays, custom request/client binders, and Java exception propagation/catching from Python tests.
  - Unlocks: basics status tests, data validation suites, responding-not-found, JSON publisher-empty, server binding/routes/routing/upload/stream tests, and custom binder specs.

- Wave 4: DI, configuration, factory, events, and AOP.
  - Implement Python field-backed beans for factory examples, `@ConfigurationBuilder`, constructor-bound `@ConfigurationProperties`, `@EachProperty` named/list beans, and `PropertySourceImporter.ImportContext` adaptation.
  - Fix event listener generic adaptation and factory/listener snippets.
  - Fix AOP on `@Factory` methods, lifecycle interceptor metadata, programmatic retry SAM conversion, propagated context SAM/function adaptation, and `@Replaces` definition loading.
  - Unlocks: config suites, factories, nullable factory if not blocked by GraalPy, `replaces`, AOP advice/lifecycle/retry, propagation, and event listener snippets.

- Wave 5: introductions and integration edges.
  - Fix introduction/proxy support for Python classes used with `@Mapper`, `@ClientWebSocket`, `Writable`, programmatic routes, and filters.
  - Fix secondary server initialization ordering so eager Python factories do not run before the GraalPy context is ready.
  - Fix docs resource visibility for `ResourceResolver` and `ResourceBundleMessageSource`.
  - Investigate Java `Throwable` extension for Python exception handlers; if not feasible, keep it explicitly tracked as a blocker with a disabled regression.
  - Unlocks: mapper specs, websocket specs, server filters, secondary server, resources/i18n, writable, programmatic routes, and exception handler snippets.

## Test Plan
- For every compiler fix: run `./gradlew --no-daemon -Dorg.gradle.java.home=/Users/graemerocher/.sdkman/candidates/java/current :micronaut-inject-python-test:test --tests <focused-spec> --max-workers=2`.
- For every runtime fix: run `./gradlew --no-daemon -Dorg.gradle.java.home=/Users/graemerocher/.sdkman/candidates/java/current :micronaut-context-python:test --tests <focused-spec> --max-workers=2`.
- For every docs item enabled: run `./gradlew --no-daemon -Dorg.gradle.java.home=/Users/graemerocher/.sdkman/candidates/java/current :test-suite-python:test --tests <fully.qualified.Spec> --max-workers=2`.
- After each wave: run the full `:test-suite-python:test`.
- Final acceptance: no active disabled docs tests except explicit external blockers, `DISABLED_TESTS.md` matches the code, and the full Python docs suite passes.

## Assumptions
- The package-info `@Introspected` target remains intentionally unsupported.
- GraalPy issues such as `GR-71643` may remain disabled if no framework-side workaround is practical.
- Public Micronaut Java APIs should not change; expected changes are internal compiler/runtime behavior plus the Python-safe `async_` import convention.

## Worktree Reminder
Use this worktree for the task:

```bash
cd /Users/graemerocher/dev/micronaut/core.pyronaut.docs
```

Do not use `/Users/graemerocher/dev/micronaut/core.pyronaut` for this docs migration task.
