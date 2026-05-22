# Python Inject Test Hardening Plan

## Objective

Raise Python inject test coverage to the same behavioral level as the Java,
Groovy, and Kotlin inject test suites where the behavior is language-neutral.
The first pass created and cataloged the tests. The next pass iterates through
`inject-python-test/DISABLED_TESTS.md`, implements Python-specific fixes, and
turns pending tests into passing coverage without regressing existing Python
test suites.

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

Fix implementation should stay under Python-specific modules unless separately
reviewed:

* `inject-python`
* `context-python`
* `inject-python-test`

The authoritative inventory commands and direct-subclass counts live in
`inject-python-test/DISABLED_TESTS.md`.

## Ground Rules

* Prefer production fixes in `inject-python` or `context-python`.
* Do not change core Micronaut modules such as `inject`, `inject-java`, or
  other non-Python modules without asking for review first.
* Do not ask for review for test-only changes under `inject-python-test`; update
  those tests and catalog entries as needed.
* Use normal spec names that match the existing Python project style.
* Prefer adding coverage to an existing Python spec when ownership is clear.
* Attribute-backed Python classes expose `PropertyElement`, not `FieldElement`.
* Include idiomatic Python forms such as `str | None` when covering nullability.
* Use Spock `@PendingFeature` for portable behavior that is not currently
  implemented or cannot currently be represented by the Python compiler model.
* Use pending reasons in this format:
  `Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-XXXX`
* Once a fix for a pending test passes, commit it before moving to the next
  unrelated fix.
* Each fix must avoid regressions in `inject-python`, `context-python`, and
  `test-suite-python`.

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

## Completion State

As of May 14, 2026:

* Normal Gradle verification resolves the GraalPy `25.1.0-SNAPSHOT` bundle
  from the included checkout; do not rely on a local bundle override.
* The latest pending ID is `PY-INJECT-0096`; use `PY-INJECT-0097` for the next
  follow-up pending case.
* The direct-subclass inventory audit has no remaining uncataloged direct
  subclasses.
* All `@PendingFeature` tests under `inject-python-test` are cataloged with
  stable `PY-INJECT-XXXX` IDs, including pre-existing Python compiler and
  runtime gaps that were outside the direct source inventory.
* The active pending catalog is currently down to 8 IDs after resolving
  `PY-INJECT-0060`.
* The most recent full verification passed with 491 tests and 8 skipped:
  `./gradlew --no-daemon --no-build-cache --max-workers=1 :micronaut-inject-python-test:test`
  after resolving `PY-INJECT-0060`.
* Per-slice verification used focused tests only; the full Python inject test
  task has now been run as the final sweep for this migration/catalog pass.
* The most recent focused verification passed for
  `ConfigurationPropertiesSpec`, `PythonClassElementSpec`,
  `PythonEventListenerSpec`, and `PyronautCompilerSpec` with 112 tests executed
  and 13 skipped.
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
* `InterfaceIntroductionAdviceSpec.groovy`: type-level around advice and generic
  method signature gaps are pending as `PY-INJECT-0061` and `PY-INJECT-0062`,
  type-argument annotation propagation is pending as `PY-INJECT-0063`, and the
  generic/type-argument/method-annotation covered cases are cataloged.
* `IntroductionGenericTypesSpec.groovy`: generic introduced method signature
  gaps are pending as `PY-INJECT-0064` through `PY-INJECT-0066`, with JVM array
  portions cataloged as unsupported.
* `FactoryMappedAdviceSpec.groovy` and `FactoryMappedAdviceReflectionSpec.groovy`:
  Java annotation-mapped factory advice is pending as `PY-INJECT-0067`.
* `AnnotatedConstructorArgumentSpec.groovy`: constructor `@Value` metadata and
  class-level/method-level AOP invocation are covered; package-private method
  portions are cataloged as unsupported.
* `FinalModifierSpec.groovy`: final class and final method AOP diagnostics are
  cataloged as unsupported.
* `IntroductionAdviceWithNewInterfaceSpec.groovy`: non-generic additional
  interface introduction is covered for concrete, abstract, and protocol-style
  Python beans; generic `ApplicationEventListener` interface introduction is
  pending as `PY-INJECT-0068`.
* `OriginatingElementsSpec.groovy`: Java static originating-element registry
  assertions are cataloged as unsupported compiler infrastructure behavior.
* `GeneratedAnnotationSpec.groovy`: Java generated-class bytecode annotation
  counting is cataloged as unsupported compiler-output behavior.
* `InjectFieldAbstractIntroductionSpec.groovy`: injected members and executable
  methods on an introduced abstract class are covered, using Python property
  setter injection methods for attribute-backed injection points.
* `MethodAdapterSpec.groovy`: event-listener adapter presence, requirements,
  inherited class metadata, event type arguments, multiple lifecycle methods,
  and async listener behavior are covered through `PythonEventListenerSpec`.
* `InterceptedAdapterSpec.groovy`: around advice on event-listener adapter
  methods is covered after the Python adapter path stopped invoking the around
  interceptor twice for one event.
* `SessionProxySpec.groovy`: Hibernate `Session`/`SessionFactory` JVM proxy
  method emission is cataloged as unsupported Java library proxy behavior.
* `IntroductionInnerInterfaceSpec.groovy`: Java nested interface introduction
  cases are cataloged as unsupported source-model behavior.
* `MyAbstractRepoSpec.groovy`: concrete methods on introduced abstract
  repository-style beans are covered; Java default interface methods remain
  unsupported.
* `AroundConstructCompileSpec.groovy`: constructor interceptor invocation,
  around-construct-only method behavior, constructor-only binding, type plus
  constructor binding, and factory-method construction binding are covered by
  `AroundConstructSpec`. Combined `@Around` plus `@AroundConstruct` behavior is
  pending as `PY-INJECT-0070`; the local Java annotation transformer fixture is
  cataloged as unsupported.
* `NamedAopAdviceSpec.groovy`: named refreshable `@EachProperty` factory beans
  and qualified AOP proxy lookup are covered by `NamedAopAdviceSpec`.
* `ExecutableSuperclassSpec.groovy`: a factory subclass inheriting class-level
  executable methods from a superclass is pending as `PY-INJECT-0071`.
* `IntroducedBeanVisitorSpec.groovy`: visitor-produced annotations on
  introduced methods are pending as `PY-INJECT-0072`; generic introduced method
  signatures from the same source area remain covered by the existing generic
  introduction pending IDs.
* `IntroducedWithRepeatableAnnotationSpec.groovy`: repeatable annotation
  metadata on inherited introduced methods is pending as `PY-INJECT-0073`.
* `MappedIntroductionOnConcreteClassSpec.groovy`: Java annotation-mapped
  introduction on a concrete class is pending as `PY-INJECT-0074`.
* `PropertyAdviceSpec.groovy`: class-level around advice on attribute-backed
  property setters is pending as `PY-INJECT-0075`.
* `IntroductionWithAroundOnConcreteClassSpec.groovy`: the portable combined
  introduction-and-around concrete-class behavior is covered for Java and Groovy
  sources; fixture matrices, exact executable method counts, JavaBean accessor
  names, and JVM multidimensional array properties are cataloged as unsupported.
* `MyIsEnumInTypeArgumentSpec.groovy`: enum metadata on generic method
  parameter type arguments is covered by `EnumElementSpec`.
* `MyRepo3Spec.groovy`: Kotlin coroutine repository introduction and
  second-round generated Kotlin source behavior are cataloged as unsupported
  Kotlin-specific source behavior.
* `CustomVisitorSpec.groovy`: controller class and route-method visitor
  filtering, controller-scoped method visits, and inject method element visits
  are covered by `CustomVisitorSpec`; Java/Groovy field visitor counts and
  field visibility portions are cataloged as unsupported. Python `@Generated`
  class visitor exclusion is pending as `PY-INJECT-0076`.
* `ElementAnnotateSpec.groovy`: type visitor mutation of executable method
  parameter metadata is covered by `AnnotateMethodParameterSpec`; broader
  class, method, return type, property, and metadata writer mutation behavior is
  covered by the existing Python annotate specs.
* `AddStereotypesFromVisitorSpec.groovy`: visitor-added scope and qualifier
  stereotypes on Java annotation fixtures are pending as `PY-INJECT-0077` and
  `PY-INJECT-0078`.
* `AnnotationMetadataSpec.groovy`: visitor-mutated method metadata based on
  method annotation default values is pending as `PY-INJECT-0079`.
* `ImportTypeElementSpec.groovy`: `@ClassImport` generated introspection
  metadata for imported Java classes and interfaces is pending as
  `PY-INJECT-0080`; exact visitor counts for imported Java fields, methods,
  constructors, and enum constants are cataloged as unsupported source-model
  behavior.
* `MixinSpec.groovy`: mixin-applied introspection and copied property, method,
  and argument annotation metadata are pending as `PY-INJECT-0081`.
* `IntroductionVisitorSpec.groovy`: visitor-visible concrete generic type
  substitution on introduced Java-interface methods is pending as
  `PY-INJECT-0082`.
* `BuildElementBuilderAopOnMethodSpec.groovy` and
  `BuildElementBuilderAopOnTypeSpec.groovy`: AOP metadata applied to associated
  beans registered through `BeanElementBuilder` is pending as `PY-INJECT-0083`
  and `PY-INJECT-0084`.
* `AnnotateReplacesSpec.groovy`: visitor-added `@Factory`, `@Bean`, and
  `@Replaces` metadata for replacement factory methods is pending as
  `PY-INJECT-0085`.
* `InterfaceConfigurationPropertiesSpec.groovy`, `InterfaceNestingSpec.groovy`,
  `EachPropertyNestingSpec.groovy`, and `RecordNestingSpec.groovy`: nested
  Python configuration class runtime binding is pending as `PY-INJECT-0090`,
  and nested `@EachProperty` list binding is pending as `PY-INJECT-0091`;
  interface proxies, Java record accessors, and Java/Groovy visibility variants
  are cataloged as unsupported, while scalar binding and nested prefix metadata
  are covered by `ConfigurationPropertiesSpec`.
* `EachBeanInterceptorSpec.groovy`: named `@EachBean` AOP target lookup is
  covered by `NamedAopAdviceSpec`; interceptor constructor injection of the
  current target `Qualifier` is pending as `PY-INJECT-0092`, while the full
  `java.sql.Connection` proxy surface is cataloged as unsupported.
* Remaining source-model and infrastructure specs with explicit filenames in
  the catalog include Groovy expression/AST fixtures, Java annotation
  mapper/transformer fixtures, Java reconstruction and visitor-context
  fixtures, Kotlin reconstruction/visitor-order fixtures, Java record/no-package
  cases, package-level `@Requires`, external Java `@Import`, and constructor-copy
  sealed-class regressions. These are catalog-only decisions because the
  language-neutral behavior is already covered by Python specs or tracked by
  existing pending IDs.
* `InheritedConfigurationReaderPrefixSpec.groovy`: supported
  `ConfigurationReader.PREFIX` alias paths with and without `basePrefix` are
  covered by `ConfigurationPropertiesSpec`; the source specs' documented
  unsupported alias-to-`ConfigurationReader.value` cases are cataloged as
  unsupported.
* `ConfigurationMetadataSpec.groovy`: Spring configuration metadata resource
  generation for Python `@ConfigurationProperties` classes is pending as
  `PY-INJECT-0086`.
* `ConfigurationJsonSchemaSpec.groovy`,
  `ConfigurationJsonSchemaDefaultsSpec.groovy`, and
  `ConfigurationJsonSchemaValidationSpec.groovy`: configuration JSON schema
  resource generation is pending as `PY-INJECT-0087`.
* `ConfigurationBuilderSpec.groovy` and `ConfigurationBuilderSpec2.groovy`:
  portable configuration builder binding is covered for Python attributes with
  write prefixes, multiple builder instances with distinct configuration
  prefixes, and inherited Java builder methods; Java private-field/getter
  diagnostics, Java interface builder fixtures, and enum `Set` fixture details
  are cataloged as unsupported.
* `FactoryWithScopedProxySpec.groovy`: non-generic refreshable factory bean
  lazy initialization and qualified refreshable factory beans are covered;
  generic scoped-proxy factory lookup is pending as `PY-INJECT-0088`; JVM
  constructor-mode diagnostics and `MockBean` fixture compilation are cataloged
  as unsupported.
* `EachPropertyParseSpec.groovy`: nested `@ConfigurationProperties` under
  `@EachProperty` computes the expected wildcard `ConfigurationReader` prefix
  and exposes wildcard property injection metadata on the generated injection
  method.
* `BeanWithPostConstructSpec.groovy` and `BeanWithPreDestroySpec.groovy`:
  lifecycle metadata, dependency injection before post-construct, post-construct
  invocation, pre-destroy invocation, and factory `preDestroy` methods are
  covered by `PythonLifecycleSpec` and `FactoryBeanMethodSpec`; Java
  private/protected visibility and Groovy anonymous-class fixture shapes are
  cataloged as unsupported.
* `BeanElementVisitorSpec.groovy`: `BeanElementVisitor` inspection of simple
  bean and factory bean metadata, bean veto, and associated bean registration
  are covered by `BeanElementSpec`.
* `ConfigurationPropertiesBuilderSpec.groovy`: portable configuration builder
  binding through factory methods, `includes`, explicit `value`/
  `configurationPrefix`, and inherited Java builder methods are covered by
  `ConfigurationBuilderSpec`; source-specific Java/Groovy fixture shapes are
  cataloged as unsupported.
* `FactoryOfBeanWithUnresolvedClassSpec.groovy`: factory bean `preDestroy`
  method search with unresolved method parameter types is pending as
  `PY-INJECT-0089` because GraalPy host-object conversion reflects the missing
  parameter type while converting the returned Java object.
* `FactoryFieldArraySpec.groovy`: portable multi-bean factory behavior is
  covered by `FactoryBeanMethodSpec` list-return tests; Java array-valued
  factory field and array lookup details are cataloged as unsupported.
* Source annotation mutation specs named `AnnotateClassSpec.groovy`,
  `AnnotateMethodSpec.groovy`, `AnnotateMethodReturnSpec.groovy`,
  `AnnotateMethodParameterSpec.groovy`, and `AnnotatePropertySpec.groovy`:
  portable visitor annotation mutation and cache behavior are covered by the
  existing Python `annotate/*Spec` suite; field/array/type-argument variants are
  cataloged as source-model-specific because Python exposes attribute-backed
  properties rather than `FieldElement`s.
* The final catalog pass also assigned stable IDs to existing Python pending
  tests that were not source-inventory migrations: Java-event listener
  interface construction (`PY-INJECT-0093`), `@EachProperty` classes
  implementing a Java interface (`PY-INJECT-0094`), method-level Python type
  variables (`PY-INJECT-0095`), and `PyronautCompiler.classpath(...)`
  (`PY-INJECT-0096`).

No uncataloged direct subclasses remain for this pass.

## Fix Iteration Plan

Treat `inject-python-test/DISABLED_TESTS.md` as the backlog for the fix phase.
Work one root cause at a time. If one implementation change resolves multiple
pending IDs, handle those IDs in one commit; otherwise keep each fix isolated.

For every resolved ID:

* Remove `@PendingFeature` from the Python test.
* Move the row from `Pending Python Tests` to a `Resolved Python Fixes` table in
  `DISABLED_TESTS.md`.
* Record the focused verification command and commit hash in the resolved row
  after committing.
* Keep any new or adjusted tests under `inject-python-test` and update them
  without a review checkpoint.

Priority order:

1. Fix generated Java compilation blockers first:
   `PY-INJECT-0021`, `PY-INJECT-0032`, `PY-INJECT-0041`, `PY-INJECT-0088`,
   `PY-INJECT-0012`, `PY-INJECT-0064`, `PY-INJECT-0065`, `PY-INJECT-0066`,
   and `PY-INJECT-0068`.
2. Fix compiler diagnostic and wrapper-generation failures:
   `PY-INJECT-0002`, `PY-INJECT-0047`, `PY-INJECT-0044`, and
   `PY-INJECT-0015`.
3. Fix Python source visitor and associated-bean gaps:
   `PY-INJECT-0025`, `PY-INJECT-0026`, `PY-INJECT-0027`, `PY-INJECT-0029`,
   `PY-INJECT-0083`, `PY-INJECT-0084`, then related visitor/resource gaps such
   as `PY-INJECT-0080`, `PY-INJECT-0081`, `PY-INJECT-0086`, and
   `PY-INJECT-0087`.
4. Continue with remaining runtime and model gaps in catalog order, grouping
   only when the shared implementation root cause is clear.

Likely first root-cause area:

* Investigate `PythonStubGenerator` conversion of parameterized Python wrapper
  types. The catalog shows several invalid generated Java cases where sourcegen
  emits parameterized class references such as `Cache<String, Integer>` or
  `Response<Integer>` for static `fromPolyglotValue(...)` calls. The fix should
  erase the generated wrapper receiver/class-literal where Java syntax requires
  a raw class while preserving generic metadata on Micronaut model elements.

Resolved `test-suite-python` work item:

* `micronaut.docs.server.exception.ExceptionHandlerSpec` failed because Python
  exceptions that extend Java `Throwable` generated only the normal Python
  construction path, not the `Value` constructor needed by the runtime to remap
  GraalPy host adapter exceptions back to the generated exception subtype.
  `PythonStubGenerator` now emits that constructor for generated Python
  `Throwable` wrappers, allowing typed Micronaut exception handlers to match.
  `./gradlew --no-daemon --no-build-cache --max-workers=1 :test-suite-python:test --tests micronaut.docs.server.exception.ExceptionHandlerSpec`
  passes after the fix.

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

Regression tests for production fixes:

```bash
./gradlew :micronaut-inject-python:test
./gradlew :micronaut-context-python:test
./gradlew :test-suite-python:test
```

Full final sweep:

```bash
./gradlew :micronaut-inject-python-test:test
```

## Acceptance Criteria

For each fix commit:

* The affected pending test no longer needs `@PendingFeature`.
* The focused `:micronaut-inject-python-test:test --tests ...` command passes.
* Relevant Python module regression tests pass:
  `:micronaut-inject-python:test` for `inject-python` changes,
  `:micronaut-context-python:test` for `context-python` changes, and
  `:test-suite-python:test` for production-code fixes.
* `DISABLED_TESTS.md` is updated so the resolved ID is no longer listed as
  pending and has a resolved entry with the passing command and commit hash.
* No non-Python module changes are included unless they were reviewed first.
* The worktree is staged and committed before starting the next unrelated fix.

For a batch/final sweep:

* Full `:micronaut-inject-python-test:test` passes.
* `:test-suite-python:test` passes.
* No pending Python test is missing from `DISABLED_TESTS.md`.
* No old parity naming is introduced.
