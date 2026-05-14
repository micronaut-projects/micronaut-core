# Python Inject Test Hardening Plan

## Objective

Raise Python inject test coverage to the same behavioral level as the Java,
Groovy, and Kotlin inject test suites where the behavior is language-neutral.
Keep this pass focused on tests, cataloging, and Gradle build reliability.

## Scope

Worktree:

* Path: `/Users/graemerocher/dev/micronaut/core.pyronaut.python-hardening`
* Branch: `python-hardening-tests`
* Base: `origin/python-support`

Source inventory is limited to direct subclasses of:

* `AbstractBeanDefinitionSpec`
* `AbstractTypeElementSpec`
* `AbstractKotlinCompilerSpec`

Source roots:

* `inject-groovy/src/test/groovy`
* `inject-groovy-test/src/main/groovy`
* `inject-java/src/test/groovy`
* `inject-java-test/src/test/groovy`
* `inject-java-test/src/main/groovy`
* `inject-kotlin/src/test/groovy`
* `inject-kotlin-test/src/main/groovy`

Python changes should stay under `inject-python-test` unless separately
approved.

The authoritative inventory commands and direct-subclass counts live in
`inject-python-test/DISABLED_TESTS.md`.

## Ground Rules

* Do not change production Python implementation in this pass.
* Add Python tests without asking for approval for each test.
* Use normal spec names that match the existing Python project style.
* Prefer adding coverage to an existing Python spec when ownership is clear.
* Attribute-backed Python classes expose `PropertyElement`, not `FieldElement`.
* Include idiomatic Python forms such as `str | None` when covering nullability.
* Use Spock `@PendingFeature` for portable behavior that is not currently
  implemented or cannot currently be represented by the Python compiler model.
* Use pending reasons in this format:
  `Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-XXXX`

## Migration Rules

Port tests that assert Micronaut behavior independent of Java, Groovy, or
Kotlin syntax:

* annotation metadata
* bean definitions and bean references
* introspection
* class elements, property elements, method elements, and constructor elements
* injection, qualifiers, providers, factories, and scopes
* AOP metadata and interceptor bindings
* generics and nullability
* visitor-visible structure and visitor-produced metadata

Do not port tests whose purpose is source-language-specific:

* Groovy AST quirks, closures, traits, dynamic property syntax, and Groovy
  reconstruction details
* Java records, package-info, package-private visibility, default interface
  methods, overload-only behavior, and Java parser diagnostics
* Kotlin data classes, companions, suspend functions, visibility, KSP behavior,
  and Kotlin reconstruction details
* expression-language specs inherited through intermediate expression fixtures

If Python already covers the same behavior, catalog it as covered instead of
duplicating the test.

## Catalog Rules

Maintain `inject-python-test/DISABLED_TESTS.md` as the decision log.

The catalog must include:

* inventory command list and direct-subclass counts
* migration rules
* pending Python tests with stable `PY-INJECT-XXXX` IDs
* intentionally unsupported source tests with reasons
* already covered equivalents and their Python spec locations

Every pending test must have a matching catalog row. Every intentionally
unsupported source behavior must have a reason.

## Work Loop

1. Mine a small source area from the direct-subclass inventory.
2. Decide whether the behavior is portable, already covered, pending, or
   intentionally unsupported.
3. Add or extend the most appropriate Python spec.
4. Add catalog entries immediately.
5. Run the focused Gradle test for the touched spec.
6. Run hygiene checks:

```bash
git diff --check
rg -n "par""ity|Par""ity|PY-PAR""ITY" inject-python-test settings.gradle
git status --short --branch
```

7. Stage and commit the verified slice before continuing.
8. Defer the full Python inject test task until the final sweep after all
   selected tests have been migrated and cataloged.

## Current Continuation State

As of May 14, 2026:

* Normal Gradle verification resolves the GraalPy `25.1.0-SNAPSHOT` bundle
  from the included checkout; do not rely on a local bundle override.
* The latest committed pending ID is `PY-INJECT-0060`; the current
  interface-introduction slice reserves `PY-INJECT-0061` through
  `PY-INJECT-0063`.
* The most recent full verification passed with 400 tests and 65 skipped.
* Per-slice verification now uses focused tests only; run the full Python inject
  test task as a final sweep once the migration/catalog pass is complete.
* Recent committed slices added or cataloged lifecycle hooks, lifecycle
  interceptor bindings, introduction-around coverage, and executable factory
  method inheritance coverage.

The latest completed source slices are:

* `ExecutableFactoryMethodSpec.groovy`: default interface method behavior is
  cataloged as unsupported, while multiple-interface factory executable
  behavior is covered by `FactoryBeanMethodSpec.test executable factory method
  with inherited client interface methods`.
* `AdviceDefinedOnFactorySpec.groovy`: class-level factory advice behavior is
  covered by `FactoryBeanMethodSpec.test class level around advice on factory
  applies to factory methods only`.

The next source area under investigation is `InterfaceIntroductionAdviceSpec.groovy`.

Current in-progress slice:

* `IntroductionAdviceSpec.test type level around advice on introduced inherited
  generic abstract method` should remain `@PendingFeature` as
  `PY-INJECT-0061` until the generated wrapper overrides the erased generic
  method signature required by the Java generic base type.
* `IntroductionAdviceSpec.test type level around advice on introduced abstract
  methods mutates arguments` should remain `@PendingFeature` as
  `PY-INJECT-0062` until type-level around advice applies to introduced
  abstract methods.
* `IntroductionGenericInterfaceSpec.test introduction generic type argument
  annotations propagate to methods` should be marked `@PendingFeature` as
  `PY-INJECT-0063` and cataloged because `Annotated[...]` type arguments on a
  Python implementation of a Java generic repository currently resolve to
  generated `Object` method signatures without propagating the annotation
  metadata to the introduced method parameter or return type.
* Catalog covered equivalents from `InterfaceIntroductionAdviceSpec.groovy` for
  generic introduction injection, type-argument map creation, and method
  annotation propagation.

Immediate next steps for this slice:

1. Add the missing `PY-INJECT-0063` catalog row and apply the matching
   `@PendingFeature` reason to the failing Python test.
2. Add covered rows for the already passing generic interface and introduced
   method metadata coverage.
3. Run the focused command:

```bash
./gradlew :micronaut-inject-python-test:test --tests io.micronaut.python.annotation.processing.test.IntroductionAdviceSpec --tests io.micronaut.python.annotation.processing.test.IntroductionGenericInterfaceSpec
```

4. Run hygiene checks.
5. Stage and commit the verified interface-introduction slice before mining the
   next source area.

Good next source areas to mine after the interface-introduction slice:

* `IntroductionGenericTypesSpec.groovy`
* `FactoryMappedAdviceSpec.groovy`

## Gradle And GraalPy

The build should resolve `25.1.0-SNAPSHOT` GraalPy artifacts from the included
GraalPy extension checkout. The normal test command should work without a
manual bundle checkout override.

If bundle resolution fails, diagnose the included repository wiring first
instead of relying on a local checkout path.

## Verification Commands

Focused test:

```bash
./gradlew :micronaut-inject-python-test:test --tests <fully.qualified.SpecName>
```

Full verification:

```bash
./gradlew :micronaut-inject-python-test:test
```

## Acceptance Criteria

* Full `:micronaut-inject-python-test:test` passes.
* All pending Python tests are listed in `DISABLED_TESTS.md`.
* All intentionally unsupported source tests are cataloged with reasons.
* Already covered source behavior points to concrete Python coverage.
* No production Python implementation changes are included unless separately
  approved.
